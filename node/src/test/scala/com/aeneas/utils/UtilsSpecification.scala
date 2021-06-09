package com.aeneas.utils

import cats.Id
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values.V3
import com.aeneas.lang.utils._
import com.aeneas.lang.v1.compiler.Terms.{FUNCTION_CALL, TRUE}
import com.aeneas.lang.v1.compiler.Types.BOOLEAN
import com.aeneas.lang.v1.evaluator.ctx.{EvaluationContext, UserFunction}
import com.aeneas.lang.v1.traits.Environment
import com.aeneas.state.diffs.smart.predef.chainId
import com.aeneas.common.state.ByteStr
import com.aeneas.transaction.smart.WavesEnvironment
import monix.eval.Coeval
import org.scalatest.{FreeSpec, Matchers}

class UtilsSpecification extends FreeSpec with Matchers {
  private val environment = new WavesEnvironment(chainId, Coeval(???), null, EmptyBlockchain, null, DirectiveSet.contractDirectiveSet, ByteStr.empty)

  "estimate()" - {
    "handles functions that depend on each other" in {
      val callee = UserFunction[Environment]("callee", 0, BOOLEAN)(TRUE)
      val caller = UserFunction[Environment]("caller", 0, BOOLEAN)(FUNCTION_CALL(callee.header, List.empty))
      val ctx = EvaluationContext.build[Id, Environment](
        environment,
        typeDefs = Map.empty,
        letDefs = Map.empty,
        functions = Seq(caller, callee)
      )
      estimate(V3, ctx).size shouldBe 2
    }
  }
}
