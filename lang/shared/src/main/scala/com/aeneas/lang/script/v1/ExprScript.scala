package com.aeneas.lang.script.v1

import cats.implicits._
import com.google.common.annotations.VisibleForTesting
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.directives.values._
import com.aeneas.lang.script.Script
import com.aeneas.lang.utils._
import com.aeneas.lang.v1.BaseGlobal
import com.aeneas.lang.v1.ContractLimits._
import com.aeneas.lang.v1.compiler.Terms._
import com.aeneas.lang.v1.estimator.ScriptEstimator
import monix.eval.Coeval

object ExprScript {

  private val Global: BaseGlobal = com.aeneas.lang.Global // Hack for IDEA

  val checksumLength = 4

  def validateBytes(bs: Array[Byte]): Either[String, Unit] =
    Either.cond(
      bs.length <= MaxExprSizeInBytes,
      (),
      s"Script is too large: ${bs.length} bytes > $MaxExprSizeInBytes bytes"
    )

  @VisibleForTesting
  def apply(x: EXPR): Either[String, Script] = apply(V1, x)

  def apply(version: StdLibVersion, x: EXPR, checkSize: Boolean = true): Either[String, Script] =
    ExprScriptImpl(version, x)
      .asRight[String]
      .flatTap(s => if (checkSize) validateBytes(s.bytes().arr) else Right(()))

  def estimateExact(
      expr: EXPR,
      version: StdLibVersion,
      estimator: ScriptEstimator
  ): Either[String, Long] =
    estimator(varNames(version, Expression), functionCosts(version), expr)

  def estimate(
      expr: EXPR,
      version: StdLibVersion,
      estimator: ScriptEstimator,
      useContractVerifierLimit: Boolean
  ): Either[String, Long] =
    for {
      complexity <- estimateExact(expr, version, estimator)
      _          <- checkComplexity(version, complexity, useContractVerifierLimit)
    } yield complexity

  def checkComplexity(
      version: StdLibVersion,
      complexity: Long,
      useContractVerifierLimit: Boolean
  ): Either[String, Unit] = {
    val limit =
      if (useContractVerifierLimit)
        MaxAccountVerifierComplexityByVersion(version)
      else
        MaxComplexityByVersion(version)

    Either.cond(
      complexity <= limit,
      (),
      s"Script is too complex: $complexity > $limit"
    )
  }

  private case class ExprScriptImpl(stdLibVersion: StdLibVersion, expr: EXPR) extends ExprScript {
    override type Expr = EXPR
    override val bytes: Coeval[ByteStr]           = Coeval.evalOnce(ByteStr(Global.serializeExpression(expr, stdLibVersion)))
    override val containsBlockV2: Coeval[Boolean] = Coeval.evalOnce(com.aeneas.lang.v1.compiler.containsBlockV2(expr))
    override val containsArray: Boolean           = com.aeneas.lang.v1.compiler.containsArray(expr)
  }
}

trait ExprScript extends Script {
  override type Expr = EXPR
  val stdLibVersion: StdLibVersion
  val expr: EXPR
}
