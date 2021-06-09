package com.aeneas.utils.doc

import cats.implicits._
import com.aeneas.lang.Global
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.v1.CTX
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.aeneas.lang.v1.traits.Environment

object RideFullContext {
  def build(ds: DirectiveSet): CTX[Environment] = {
    val wavesCtx  = WavesContext.build(ds)
    val cryptoCtx = CryptoContext.build(Global, ds.stdLibVersion).withEnvironment[Environment]
    val pureCtx = PureContext.build(Global, ds).withEnvironment[Environment]
    pureCtx |+| cryptoCtx |+| wavesCtx
  }
}
