package com.aeneas.lang.v1

import cats.implicits._
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values._
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.aeneas.lang.v1.repl.node.ErrorMessageEnvironment
import com.aeneas.lang.v1.repl.node.http.{NodeConnectionSettings, WebEnvironment}
import com.aeneas.lang.v1.traits.Environment

import scala.concurrent.Future

package object repl {
  val global: BaseGlobal = com.aeneas.lang.Global
  val internalVarPrefixes: Set[Char] = Set('@', '$')
  val internalFuncPrefix: String = "_"

  val version = V4
  val directives: DirectiveSet = DirectiveSet(version, Account, DApp).getOrElse(???)

  val initialCtx: CTX[Environment] =
    CryptoContext.build(global, version).withEnvironment[Environment]  |+|
    PureContext.build(global, version).withEnvironment[Environment] |+|
    WavesContext.build(directives)

  def buildEnvironment(settings: Option[NodeConnectionSettings]): Environment[Future] =
    settings.fold(ErrorMessageEnvironment: Environment[Future])(WebEnvironment)
}
