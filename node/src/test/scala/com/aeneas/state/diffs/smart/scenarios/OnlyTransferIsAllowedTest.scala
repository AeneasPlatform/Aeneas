package com.aeneas.state.diffs.smart.scenarios

import com.aeneas.common.utils.EitherExt2
import com.aeneas.db.WithState
import com.aeneas.lagonaki.mocks.TestBlock
import com.aeneas.lang.directives.values._
import com.aeneas.lang.utils._
import com.aeneas.lang.v1.compiler.ExpressionCompiler
import com.aeneas.lang.v1.parser.Parser
import com.aeneas.state.diffs._
import com.aeneas.state.diffs.smart._
import com.aeneas.{NoShrink, TransactionGen}
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class OnlyTransferIsAllowedTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {

  property("transfer is allowed but lease is not due to predicate") {

    val scriptText =
      s"""
         |
         | match tx {
         |  case ttx: TransferTransaction | MassTransferTransaction =>
         |     sigVerify(ttx.bodyBytes,ttx.proofs[0],ttx.senderPublicKey)
         |  case _ =>
         |     false
         | }
      """.stripMargin
    val untyped         = Parser.parseExpr(scriptText).get.value
    val transferAllowed = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untyped).explicitGet()._1

    forAll(preconditionsTransferAndLease(transferAllowed)) {
      case (genesis, script, lease, transfer) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(transfer)), smartEnabledFS) { case _ => () }
        assertDiffEi(Seq(TestBlock.create(Seq(genesis, script))), TestBlock.create(Seq(lease)), smartEnabledFS)(totalDiffEi =>
          totalDiffEi should produce("TransactionNotAllowedByScript"))
    }
  }

}
