package com.aeneas.state.diffs

import cats.implicits._
import com.aeneas.common.utils.EitherExt2
import com.aeneas.lang.Global
import com.aeneas.lang.contract.DApp
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values.{Account, Expression, ScriptType, StdLibVersion, V3, DApp => DAppType}
import com.aeneas.lang.v1.compiler.{ContractCompiler, ExpressionCompiler, Terms}
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.aeneas.lang.v1.parser.Expressions.{DAPP, EXPR}
import com.aeneas.lang.v1.traits.Environment
import com.aeneas.state.diffs.FeeValidation._
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.smart.InvokeScriptTransaction
import org.scalacheck.Gen

package object ci {
  def ciFee(sc: Int = 0, nonNftIssue: Int = 0): Gen[Long] =
    Gen.choose(
      FeeUnit * FeeConstants(InvokeScriptTransaction.typeId) + sc * ScriptExtraFee + nonNftIssue * FeeConstants(IssueTransaction.typeId) * FeeUnit,
      FeeUnit * FeeConstants(InvokeScriptTransaction.typeId) + (sc + 1) * ScriptExtraFee - 1 + nonNftIssue * FeeConstants(IssueTransaction.typeId) * FeeUnit
    )

  def compileContractFromExpr(expr: DAPP, version: StdLibVersion = V3): DApp = {
    val ctx =
      PureContext.build(Global, version).withEnvironment[Environment] |+|
        CryptoContext.build(Global, version).withEnvironment[Environment] |+|
        WavesContext.build(
          DirectiveSet(version, Account, DAppType).explicitGet()
        )
    ContractCompiler(ctx.compilerContext, expr, version).explicitGet()
  }

  def compileExpr(expr: EXPR, version: StdLibVersion, scriptType: ScriptType): Terms.EXPR = {
    val ctx =
      PureContext.build(Global, version).withEnvironment[Environment] |+|
        CryptoContext.build(Global, version).withEnvironment[Environment] |+|
        WavesContext.build(
          DirectiveSet(version, scriptType, Expression).explicitGet()
        )
    ExpressionCompiler(ctx.compilerContext, expr).explicitGet()._1
  }
}
