package com.aeneas.state.diffs.smart.scenarios

import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.db.WithState
import com.aeneas.lagonaki.mocks.TestBlock
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.lang.v1.compiler.Terms._
import com.aeneas.state.diffs.smart.smartEnabledFS
import com.aeneas.state.diffs.{ENOUGH_AMT, produce}
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.transfer._
import com.aeneas.transaction.{GenesisTransaction, Proofs}
import com.aeneas.{NoShrink, TransactionGen}
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class OneProofForNonScriptedAccountTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {

  property("exactly 1 proof required for non-scripted accounts") {
    val s = for {
      master    <- accountGen
      recepient <- accountGen
      amt       <- positiveLongGen
      fee       <- smallFeeGen
      ts        <- positiveIntGen
      genesis = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      setScript <- selfSignedSetScriptTransactionGenP(master, ExprScript(TRUE).explicitGet())
      transfer = TransferTransaction.selfSigned(2.toByte, master, recepient.toAddress, Waves, amt, Waves, fee, ByteStr.empty,  ts).explicitGet()
    } yield (genesis, setScript, transfer)

    forAll(s) {
      case (genesis, script, transfer) =>
        val transferWithExtraProof = transfer.copy(proofs = Proofs(Seq(ByteStr.empty, ByteStr(Array(1: Byte)))))
        assertDiffEi(Seq(TestBlock.create(Seq(genesis))), TestBlock.create(Seq(transferWithExtraProof)), smartEnabledFS)(
          totalDiffEi => totalDiffEi should produce("must have exactly 1 proof")
        )
    }
  }

}
