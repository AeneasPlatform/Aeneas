package com.aeneas.lang

import cats.kernel.Monoid
import com.aeneas.lang.directives.values.V2
import com.aeneas.lang.v1.compiler.ExpressionCompiler
import com.aeneas.lang.v1.compiler.Terms.EXPR
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}

object JavaAdapter {
  private val version = V2

  lazy val ctx =
    Monoid.combineAll(
      Seq(
        CryptoContext.compilerContext(Global, version),
        WavesContext.build(???).compilerContext,
        PureContext.build(Global, version).compilerContext
      ))

  def compile(input: String): EXPR = {
    ExpressionCompiler
      .compile(input, ctx)
      .fold(
        error => throw new IllegalArgumentException(error),
        res => res
      )
  }
}
