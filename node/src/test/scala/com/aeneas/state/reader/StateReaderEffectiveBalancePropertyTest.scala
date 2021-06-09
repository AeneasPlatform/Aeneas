package com.aeneas.state.reader

import com.aeneas.common.utils.EitherExt2
import com.aeneas.db.WithState
import com.aeneas.features.BlockchainFeatures._
import com.aeneas.lagonaki.mocks.TestBlock.{create => block}
import com.aeneas.settings.TestFunctionalitySettings.Enabled
import com.aeneas.state.LeaseBalance
import com.aeneas.state.diffs._
import com.aeneas.transaction.GenesisTransaction
import com.aeneas.transaction.lease.LeaseTransaction
import com.aeneas.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class StateReaderEffectiveBalancePropertyTest extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {
  property("No-interactions genesis account's effectiveBalance doesn't depend on depths") {
    val setup: Gen[(GenesisTransaction, Int, Int, Int)] = for {
      master <- accountGen
      ts     <- positiveIntGen
      genesis: GenesisTransaction = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      emptyBlocksAmt <- Gen.choose(1, 10)
      atHeight       <- Gen.choose(1, 20)
      confirmations  <- Gen.choose(1, 20)
    } yield (genesis, emptyBlocksAmt, atHeight, confirmations)

    forAll(setup) {
      case (genesis: GenesisTransaction, emptyBlocksAmt, atHeight, confirmations) =>
        val genesisBlock = block(Seq(genesis))
        val nextBlocks   = List.fill(emptyBlocksAmt - 1)(block(Seq.empty))
        assertDiffAndState(genesisBlock +: nextBlocks, block(Seq.empty)) { (_, newState) =>
          newState.effectiveBalance(genesis.recipient, confirmations) shouldBe genesis.amount
        }
    }
  }

  property("Negative generating balance case") {
    val fs  = Enabled.copy(preActivatedFeatures = Map(SmartAccounts.id -> 0, SmartAccountTrading.id -> 0))
    val Fee = 100000
    val setup = for {
      master <- accountGen
      ts     <- positiveLongGen
      genesis = GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet()
      leaser <- accountGen
      xfer1  <- transferGeneratorPV2(ts + 1, master, leaser.toAddress, ENOUGH_AMT / 3)
      lease1 = LeaseTransaction.selfSigned(2.toByte, leaser, master.toAddress, xfer1.amount - Fee, Fee, ts + 2).explicitGet()
      xfer2 <- transferGeneratorPV2(ts + 3, master, leaser.toAddress, ENOUGH_AMT / 3)
      lease2 = LeaseTransaction.selfSigned(2.toByte, leaser, master.toAddress, xfer2.amount - Fee, Fee, ts + 4).explicitGet()
    } yield (leaser, genesis, xfer1, lease1, xfer2, lease2)

    forAll(setup) {
      case (leaser, genesis, xfer1, lease1, xfer2, lease2) =>
        assertDiffAndState(Seq(block(Seq(genesis)), block(Seq(xfer1, lease1))), block(Seq(xfer2, lease2)), fs) { (_, state) =>
          val portfolio       = state.wavesPortfolio(lease1.sender.toAddress)
          val expectedBalance = xfer1.amount + xfer2.amount - 2 * Fee
          portfolio.balance shouldBe expectedBalance
          state.generatingBalance(leaser.toAddress, state.lastBlockId) shouldBe 0
          portfolio.lease shouldBe LeaseBalance(0, expectedBalance)
          portfolio.effectiveBalance shouldBe 0
        }
    }
  }
}
