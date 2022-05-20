package com.aeneas.state.diffs.smart.performance

import com.aeneas.account.{KeyPair, PublicKey}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.db.WithState
import com.aeneas.lagonaki.mocks.TestBlock
import com.aeneas.lang.directives.values._
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.lang.utils._
import com.aeneas.lang.v1.compiler.ExpressionCompiler
import com.aeneas.lang.v1.compiler.Terms._
import com.aeneas.lang.v1.parser.Parser
import com.aeneas.metrics.Instrumented
import com.aeneas.state.diffs._
import com.aeneas.state.diffs.smart._
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.GenesisTransaction
import com.aeneas.transaction.transfer._
import com.aeneas.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class SigVerifyPerformanceTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {

  private val AmtOfTxs = 10000

  private def simpleSendGen(from: KeyPair, to: PublicKey, ts: Long): Gen[TransferTransaction] =
    for {
      amt <- smallFeeGen
      fee <- smallFeeGen
    } yield TransferTransaction.selfSigned(1.toByte, from, to.toAddress, Waves, amt, Waves, fee, ByteStr.empty,  ts).explicitGet()

  private def scriptedSendGen(from: KeyPair, to: PublicKey, ts: Long): Gen[TransferTransaction] =
    for {
      amt <- smallFeeGen
      fee <- smallFeeGen
    } yield TransferTransaction.selfSigned(2.toByte, from, to.toAddress, Waves, amt, Waves, fee, ByteStr.empty,  ts).explicitGet()

  private def differentTransfers(typed: EXPR) =
    for {
      master    <- accountGen
      recipient <- accountGen
      ts        <- positiveIntGen
      amt       <- smallFeeGen
      fee       <- smallFeeGen
      genesis = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      setScript <- selfSignedSetScriptTransactionGenP(master, ExprScript(typed).explicitGet())
      transfer       = simpleSendGen(master, recipient.publicKey, ts)
      scriptTransfer = scriptedSendGen(master, recipient.publicKey, ts)
      transfers       <- Gen.listOfN(AmtOfTxs, transfer)
      scriptTransfers <- Gen.listOfN(AmtOfTxs, scriptTransfer)
    } yield (genesis, setScript, transfers, scriptTransfers)

  ignore("parallel native signature verification vs sequential scripted signature verification") {
    val textScript    = "sigVerify(tx.bodyBytes,tx.proofs[0],tx.senderPk)"
    val untypedScript = Parser.parseExpr(textScript).get.value
    val typedScript   = ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untypedScript).explicitGet()._1

    forAll(differentTransfers(typedScript)) {
      case (gen, setScript, transfers, scriptTransfers) =>
        def simpleCheck(): Unit = assertDiffAndState(Seq(TestBlock.create(Seq(gen))), TestBlock.create(transfers), smartEnabledFS) { case _ => }
        def scriptedCheck(): Unit =
          assertDiffAndState(Seq(TestBlock.create(Seq(gen, setScript))), TestBlock.create(scriptTransfers), smartEnabledFS) {
            case _ =>
          }

        val simeplCheckTime   = Instrumented.withTimeMillis(simpleCheck())._2
        val scriptedCheckTime = Instrumented.withTimeMillis(scriptedCheck())._2
        println(s"[parallel] simple check time: $simeplCheckTime ms,\t [seqential] scripted check time: $scriptedCheckTime ms")
    }

  }
}
