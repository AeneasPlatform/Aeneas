package com.aeneas.lang

import cats.Id
import cats.kernel.Monoid
import com.aeneas.common.utils.EitherExt2
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.directives.values._
import com.aeneas.lang.directives.{DirectiveDictionary, DirectiveSet}
import com.aeneas.lang.v1.compiler.Types.CASETYPEREF
import com.aeneas.lang.v1.compiler.{CompilerContext, DecompilerContext}
import com.aeneas.lang.v1.evaluator.ctx.EvaluationContext
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.aeneas.lang.v1.traits.domain.{BlockInfo, Recipient, ScriptAssetInfo, Tx}
import com.aeneas.lang.v1.traits.{DataType, Environment}
import com.aeneas.lang.v1.{BaseGlobal, CTX, FunctionHeader}
import monix.eval.Coeval

import scala.collection.mutable

package object utils {

  private val Global: BaseGlobal = com.aeneas.lang.Global // Hack for IDEA

  val environment = new Environment[Id] {
    override def height: Long                                                                                    = 0
    override def chainId: Byte                                                                                   = 1: Byte
    override def inputEntity: Environment.InputEntity                                                            = null
    override val txId: ByteStr                                                                                   = ByteStr.empty
    override def transactionById(id: Array[Byte]): Option[Tx]                                                    = ???
    override def transferTransactionById(id: Array[Byte]): Option[Tx.Transfer]                                   = ???
    override def transactionHeightById(id: Array[Byte]): Option[Long]                                            = ???
    override def assetInfoById(id: Array[Byte]): Option[ScriptAssetInfo]                                         = ???
    override def lastBlockOpt(): Option[BlockInfo]                                                               = ???
    override def blockInfoByHeight(height: Int): Option[BlockInfo]                                               = ???
    override def data(addressOrAlias: Recipient, key: String, dataType: DataType): Option[Any]                   = ???
    override def accountBalanceOf(addressOrAlias: Recipient, assetId: Option[Array[Byte]]): Either[String, Long] = ???
    override def accountWavesBalanceOf(addressOrAlias: Recipient): Either[String, Environment.BalanceDetails]    = ???
    override def resolveAlias(name: String): Either[String, Recipient.Address]                                   = ???
    override def tthis: Environment.Tthis                                                                        = ???
    override def multiPaymentAllowed: Boolean                                                                    = true
    override def transferTransactionFromProto(b: Array[Byte]): Option[Tx.Transfer]                               = ???
    override def addressFromString(address: String): Either[String, Recipient.Address]                           = ???
  }

  val lazyContexts: Map[DirectiveSet, Coeval[CTX[Environment]]] = {
    val directives = for {
      version    <- DirectiveDictionary[StdLibVersion].all
      cType      <- DirectiveDictionary[ContentType].all
      scriptType <- DirectiveDictionary[ScriptType].all
    } yield DirectiveSet(version, scriptType, cType)
    directives
      .filter(_.isRight)
      .map(_.explicitGet())
      .map(ds => {
        val version = ds.stdLibVersion
        val ctx = Coeval.evalOnce(
          Monoid.combineAll(
            Seq(
              PureContext.build(Global, version).withEnvironment[Environment],
              CryptoContext.build(Global, version).withEnvironment[Environment],
              WavesContext.build(ds)
            )
          )
        )
        ds -> ctx
      })
      .toMap
  }

  private val lazyFunctionCosts: Map[DirectiveSet, Coeval[Map[FunctionHeader, Coeval[Long]]]] =
    lazyContexts.map(el => (el._1, el._2.map(ctx => estimate(el._1.stdLibVersion, ctx.evaluationContext[Id](environment)))))

  def functionCosts(version: StdLibVersion): Map[FunctionHeader, Coeval[Long]] =
    functionCosts(DirectiveSet(version, Account, Expression).explicitGet())

  def functionCosts(ds: DirectiveSet): Map[FunctionHeader, Coeval[Long]] =
    lazyFunctionCosts(ds)()

  def estimate(version: StdLibVersion, ctx: EvaluationContext[Environment, Id]): Map[FunctionHeader, Coeval[Long]] = {
    val costs: mutable.Map[FunctionHeader, Coeval[Long]] = mutable.Map.from(ctx.typeDefs.collect {
      case (typeName, CASETYPEREF(_, fields, hidden)) if (!hidden || version < V4) => FunctionHeader.User(typeName) -> Coeval.now(fields.size.toLong)
    })

    ctx.functions.values.foreach { func =>
      val cost = func.costByLibVersion(version)
      costs += func.header -> Coeval.now(cost)
    }

    costs.toMap
  }

  def compilerContext(version: StdLibVersion, cType: ContentType, isAssetScript: Boolean): CompilerContext = {
    val ds = DirectiveSet(version, ScriptType.isAssetScript(isAssetScript), cType).explicitGet()
    compilerContext(ds)
  }

  def compilerContext(ds: DirectiveSet): CompilerContext = lazyContexts(ds.copy(imports = Imports()))().compilerContext

  def getDecompilerContext(v: StdLibVersion, cType: ContentType): DecompilerContext =
    lazyContexts(DirectiveSet(v, Account, cType).explicitGet())().decompilerContext

  def varNames(version: StdLibVersion, cType: ContentType): Set[String] =
    compilerContext(version, cType, isAssetScript = false).varDefs.keySet
}
