package com.aeneas.state.diffs

import cats.implicits._
import com.aeneas.features.EstimatorProvider._
import com.aeneas.features.ComplexityCheckPolicyProvider._
import com.aeneas.lang.ValidationError
import com.aeneas.lang.contract.DApp
import com.aeneas.lang.directives.values.StdLibVersion
import com.aeneas.lang.script.ContractScript
import com.aeneas.lang.script.ContractScript.ContractScriptImpl
import com.aeneas.lang.v1.estimator.ScriptEstimator
import com.aeneas.state.{AccountScriptInfo, Blockchain, Diff, LeaseBalance, Portfolio}
import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.transaction.smart.SetScriptTransaction

object SetScriptTransactionDiff {
  def apply(blockchain: Blockchain)(tx: SetScriptTransaction): Either[ValidationError, Diff] =
    for {
      callableComplexities <- tx.script match {
        case Some(ContractScriptImpl(version, dApp)) => estimate(blockchain, version, dApp)
        case _                                       => Right(Map[Int, Map[String, Long]]())
      }
      verifierWithComplexity <- DiffsCommon.countVerifierComplexity(tx.script, blockchain, isAsset = false)
      scriptWithComplexities = verifierWithComplexity.map {
        case (script, verifierComplexity) =>
          AccountScriptInfo(tx.sender, script, verifierComplexity, callableComplexities)
      }
    } yield Diff(
      tx = tx,
      portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty)),
      scripts = Map(tx.sender.toAddress    -> scriptWithComplexities),
      scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)
    )

  private def estimate(
      blockchain: Blockchain,
      version: StdLibVersion,
      dApp: DApp
  ): Either[GenericError, Map[Int, Map[String, Long]]] = {
    val callables = dApp.copy(verifierFuncOpt = None)
    val actualComplexities =
      for {
        currentComplexity <- ContractScript.estimateComplexity(version, callables, blockchain.estimator, blockchain.useReducedVerifierComplexityLimit)
        nextComplexities  <- estimateNext(blockchain, version, callables)
        complexitiesByEstimator = (currentComplexity :: nextComplexities).mapWithIndex {
          case ((_, complexitiesByCallable), i) => (i + blockchain.estimator.version, complexitiesByCallable)
        }.toMap
      } yield complexitiesByEstimator

    actualComplexities.leftMap(GenericError(_))
  }

  private def estimateNext(
      blockchain: Blockchain,
      version: StdLibVersion,
      dApp: DApp
  ): Either[String, List[(Long, Map[String, Long])]] =
    ScriptEstimator.all
      .drop(blockchain.estimator.version)
      .traverse(se => ContractScript.estimateComplexityExact(version, dApp, se)
        .map { case ((_, maxComplexity), complexities) => (maxComplexity, complexities) }
      )
}
