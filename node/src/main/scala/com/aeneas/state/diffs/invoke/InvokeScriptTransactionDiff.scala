package com.aeneas.state.diffs.invoke

import cats.Id
import cats.implicits._
import com.aeneas.account._
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.features.BlockchainFeatures
import com.aeneas.features.EstimatorProvider._
import com.aeneas.features.FunctionCallPolicyProvider._
import com.aeneas.lang._
import com.aeneas.lang.contract.DApp
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values.{DApp => DAppType, _}
import com.aeneas.lang.script.ContractScript.ContractScriptImpl
import com.aeneas.lang.v1.ContractLimits
import com.aeneas.lang.v1.compiler.ContractCompiler
import com.aeneas.lang.v1.compiler.Terms._
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.aeneas.lang.v1.evaluator.ctx.{EvaluationContext, LazyVal}
import com.aeneas.lang.v1.evaluator.{ContractEvaluator, IncompleteResult, Log, ScriptResult, ScriptResultV3, ScriptResultV4}
import com.aeneas.lang.v1.traits.Environment
import com.aeneas.lang.v1.traits.domain._
import com.aeneas.metrics._
import com.aeneas.settings.Constants
import com.aeneas.state._
import com.aeneas.state.diffs.FeeValidation._
import com.aeneas.transaction.Transaction
import com.aeneas.transaction.TxValidationError._
import com.aeneas.transaction.smart.script.ScriptRunner.TxOrd
import com.aeneas.transaction.smart.script.trace.{InvokeScriptTrace, TracedResult}
import com.aeneas.transaction.smart.{DApp => DAppTarget, _}
import monix.eval.Coeval
import shapeless.Coproduct

import scala.util.{Right, Try}

object InvokeScriptTransactionDiff {

  private val stats = TxProcessingStats
  import stats.TxTimerExt

  def apply(blockchain: Blockchain, blockTime: Long, skipExecution: Boolean = false, verifyAssets: Boolean = true)(
      tx: InvokeScriptTransaction
  ): TracedResult[ValidationError, Diff] = {

    val dAppAddressEi = blockchain.resolveAlias(tx.dAppAddressOrAlias)
    val accScriptEi   = dAppAddressEi.map(blockchain.accountScript)
    val functionCall  = tx.funcCall

    accScriptEi match {
      case Right(Some(AccountScriptInfo(pk, ContractScriptImpl(version, contract), _, callableComplexities))) =>
        for {
          _           <- TracedResult.wrapE(checkCall(functionCall, blockchain).leftMap(GenericError.apply))
          dAppAddress <- TracedResult(dAppAddressEi)

          feeInfo <- TracedResult(InvokeDiffsCommon.calcFee(blockchain, tx))

          directives <- TracedResult.wrapE(DirectiveSet(version, Account, DAppType).leftMap(GenericError.apply))
          payments   <- TracedResult.wrapE(AttachedPaymentExtractor.extractPayments(tx, version, blockchain, DAppTarget).leftMap(GenericError.apply))
          tthis = Coproduct[Environment.Tthis](Recipient.Address(ByteStr(dAppAddress.bytes)))
          input      <- TracedResult.wrapE(buildThisValue(Coproduct[TxOrd](tx: Transaction), blockchain, directives, tthis).leftMap(GenericError.apply))

          invocationComplexity <- TracedResult {
            InvokeDiffsCommon.getInvocationComplexity(blockchain, tx, callableComplexities, dAppAddress)
          }

          stepLimit = ContractLimits.MaxComplexityByVersion(version)
          stepsNumber = if (invocationComplexity % stepLimit == 0)
            invocationComplexity / stepLimit
          else
            invocationComplexity / stepLimit + 1

          _ <- TracedResult {
            val minFee    = FeeConstants(InvokeScriptTransaction.typeId) * FeeUnit * stepsNumber
            val assetName = tx.assetFee._1.fold("WAVES")(_.id.toString)
            val txName    = Constants.TransactionNames(InvokeScriptTransaction.typeId)
            Either.cond(
              feeInfo._1 >= minFee,
              (),
              GenericError(
                s"Fee in $assetName for $txName (${tx.assetFee._2} in $assetName)" +
                  s" does not exceed minimal value of $minFee WAVES."
              )
            )
          }

          result <- if (!skipExecution) {
            for {
              scriptResult <- {
                val scriptResultE = stats.invokedScriptExecution.measureForType(InvokeScriptTransaction.typeId)({
                  val invoker = tx.sender.toAddress
                  val invocation = ContractEvaluator.Invocation(
                    functionCall,
                    Recipient.Address(ByteStr(invoker.bytes)),
                    tx.sender,
                    payments,
                    ByteStr(tx.dAppAddressOrAlias.bytes),
                    tx.id(),
                    tx.fee,
                    tx.feeAssetId.compatId
                  )
                  val height = blockchain.height
                  val environment = new WavesEnvironment(
                    AddressScheme.current.chainId,
                    Coeval.evalOnce(input),
                    Coeval(height),
                    blockchain,
                    tthis,
                    directives,
                    tx.id(),
                    !blockchain.isFeatureActivated(BlockchainFeatures.BlockV5, height) && tx.dAppAddressOrAlias.isInstanceOf[Alias]
                  )

                  //to avoid continuations when evaluating underestimated by EstimatorV2 scripts
                  val fullLimit =
                    if (blockchain.estimator == ScriptEstimatorV2)
                      Int.MaxValue
                    else
                      ContractLimits.MaxComplexityByVersion(version)

                  val failFreeLimit =
                    if (blockchain.isFeatureActivated(BlockchainFeatures.BlockV5))
                      ContractLimits.FailFreeInvokeComplexity
                    else
                      fullLimit

                  for {
                    (failFreeResult, evaluationCtx, failFreeLog) <-
                      evaluateV2(version, contract, directives, invocation, environment, failFreeLimit)
                    (result, log) <-
                      failFreeResult match {
                        case IncompleteResult(expr, unusedComplexity) =>
                          continueEvaluation(version, expr, evaluationCtx, fullLimit - failFreeLimit + unusedComplexity, tx.id(), invocationComplexity)
                        case _ =>
                          Right((failFreeResult, Nil))
                      }
                  } yield (result, failFreeLog ::: log)
                })
                TracedResult(
                  scriptResultE,
                  List(InvokeScriptTrace(tx.dAppAddressOrAlias, functionCall, scriptResultE.map(_._1), scriptResultE.fold(_.log, _._2)))
                )
              }

              doProcessActions = InvokeDiffsCommon.processActions(
                _,
                version,
                dAppAddress,
                pk,
                feeInfo,
                invocationComplexity,
                tx,
                blockchain,
                blockTime,
                verifyAssets
              )

              resultDiff <- scriptResult._1 match {
                case ScriptResultV3(dataItems, transfers) => doProcessActions(dataItems ::: transfers)
                case ScriptResultV4(actions)              => doProcessActions(actions)
                case _: IncompleteResult                  => TracedResult(Left(GenericError("Unexpected IncompleteResult")))
              }
            } yield resultDiff
          } else TracedResult.wrapValue(InvokeDiffsCommon.paymentsPart(tx, dAppAddress, feeInfo._2))
        } yield result

      case Left(l) => TracedResult(Left(l))
      case _       => TracedResult(Left(GenericError(s"No contract at address ${tx.dAppAddressOrAlias}")))
    }
  }

  private def evaluateV2(
      version: StdLibVersion,
      contract: DApp,
      directives: DirectiveSet,
      invocation: ContractEvaluator.Invocation,
      environment: WavesEnvironment,
      limit: Int
  ): Either[ScriptExecutionError, (ScriptResult, EvaluationContext[Environment, Id], Log[Id])] = {
    val wavesContext = WavesContext.build(directives)
    val ctx =
      PureContext.build(Global, version).withEnvironment[Environment] |+|
        CryptoContext.build(Global, version).withEnvironment[Environment] |+|
        wavesContext.copy(vars = Map())

    val freezingLets  = wavesContext.evaluationContext(environment).letDefs
    val evaluationCtx = ctx.evaluationContext(environment)

    Try(ContractEvaluator.applyV2(evaluationCtx, freezingLets, contract, invocation, version, limit))
      .fold(
        e => Left((e.getMessage, Nil)),
        _.map { case (result, log) => (result, evaluationCtx, log) }
      )
      .leftMap {
        case (error, log) => ScriptExecutionError.dAppExecution(error, log)
      }
  }

  private def continueEvaluation(
      version: StdLibVersion,
      expr: EXPR,
      evaluationCtx: EvaluationContext[Environment, Id],
      limit: Int,
      transactionId: ByteStr,
      failComplexity: Long
  ): Either[FailedTransactionError, (ScriptResult, Log[Id])] =
    Try(ContractEvaluator.applyV2(evaluationCtx, Map[String, LazyVal[Id]](), expr, version, transactionId, limit))
      .fold(e => Left((e.getMessage, Nil)), identity)
      .leftMap { case (error, log) => FailedTransactionError.dAppExecution(error, failComplexity, log) }

  private def checkCall(fc: FUNCTION_CALL, blockchain: Blockchain): Either[ExecutionError, Unit] = {
    val (check, expectedTypes) =
      if (blockchain.callableListArgumentsAllowed)
        (
          fc.args.forall(arg => arg.isInstanceOf[EVALUATED] && !arg.isInstanceOf[CaseObj]),
          ContractCompiler.allowedCallableTypesV4
        )
      else
        (
          fc.args.forall(arg => arg.isInstanceOf[EVALUATED] && !arg.isInstanceOf[CaseObj] && !arg.isInstanceOf[ARR]),
          ContractCompiler.primitiveCallableTypes
        )
    Either.cond(
      check,
      (),
      s"All arguments of InvokeScript must be one of the types: ${expectedTypes.mkString(", ")}"
    )
  }

}
