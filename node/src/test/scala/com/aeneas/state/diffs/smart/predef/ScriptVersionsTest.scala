package com.aeneas.state.diffs.smart.predef

import com.aeneas.TransactionGen
import com.aeneas.lang.Testing
import com.aeneas.lang.directives.values._
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.lang.utils._
import com.aeneas.lang.v1.compiler.ExpressionCompiler
import com.aeneas.lang.v1.compiler.Terms.EVALUATED
import com.aeneas.lang.v1.parser.Parser
import com.aeneas.state.Blockchain
import com.aeneas.state.diffs._
import com.aeneas.transaction.Transaction
import com.aeneas.transaction.smart.script.ScriptRunner
import com.aeneas.utils.EmptyBlockchain
import org.scalacheck.Gen
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}
import shapeless.Coproduct

class ScriptVersionsTest extends FreeSpec with PropertyChecks with Matchers with TransactionGen {
  def eval[T <: EVALUATED](script: String,
                           version: StdLibVersion,
                           tx: Transaction = transferV2Gen.sample.get,
                           blockchain: Blockchain = EmptyBlockchain): Either[String, EVALUATED] = {
    val expr = Parser.parseExpr(script).get.value
    for {
      compileResult <- ExpressionCompiler(compilerContext(version, Expression, isAssetScript = false), expr)
      (typedExpr, _) = compileResult
      s <- ExprScript(version, typedExpr, checkSize = false)
      r <- ScriptRunner(Coproduct(tx), blockchain, s, isAssetScript = false, null)._2
    } yield r

  }

  val duplicateNames =
    """
      |match tx {
      |  case _: TransferTransaction => true
      |  case _ => false
      |}
    """.stripMargin

  val orderTypeBindings = "let t = Buy; t == Buy"

  private val txById =
    """
      | let t = transactionById(base64'')
      | !isDefined(t)
      |
    """.stripMargin

  private val transferTxById =
    """
      | let t = transferTransactionById(base64'')
      | !isDefined(t)
      |
    """.stripMargin

  "ScriptV1 allows duplicate names" in {
    forAll(transferV2Gen.flatMap(tx => Gen.oneOf(V1, V2).map(v => (tx, v)))) {
      case (tx, v) =>
        eval[EVALUATED](duplicateNames, v, tx) shouldBe Testing.evaluated(true)
    }
  }

  "ScriptV1" - {
    "does not have bindings defined in V2" in {
      eval[EVALUATED](orderTypeBindings, V1) should produce("definition of 'Buy' is not found")
    }

    "allow transactionById" in {
      eval[EVALUATED](txById, V2) shouldBe Testing.evaluated(true)
    }
  }

  "ScriptV2" - {
    "allows duplicate names" in {
      forAll(transferV2Gen) { tx =>
        eval[EVALUATED](duplicateNames, V2, tx) shouldBe Testing.evaluated(true)
      }
    }

    "has bindings defined in V2" in {
      eval[EVALUATED](orderTypeBindings, V2) shouldBe Testing.evaluated(true)
    }

    "allow transactionById" in {
      eval[EVALUATED](txById, V2) shouldBe Testing.evaluated(true)
    }
  }

  "ScriptV3" - {
    "disallow transactionById" in {
        eval[EVALUATED](txById, V3) should produce("Can't find a function 'transactionById'")
    }

    "add transferTransactionById" in {
      eval[EVALUATED](transferTxById, V3) shouldBe Testing.evaluated(true)
    }
  }
}
