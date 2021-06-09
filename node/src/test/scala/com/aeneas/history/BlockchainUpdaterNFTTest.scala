package com.aeneas.history

import com.aeneas._
import com.aeneas.account.Address
import com.aeneas.block.{Block, MicroBlock}
import com.aeneas.common.utils.EitherExt2
import com.aeneas.features.BlockchainFeatures
import com.aeneas.history.Domain.BlockchainUpdaterExt
import com.aeneas.lagonaki.mocks.TestBlock
import com.aeneas.lang.v1.FunctionHeader
import com.aeneas.lang.v1.compiler.Terms.FUNCTION_CALL
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.state.diffs
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.GenesisTransaction
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.smart.InvokeScriptTransaction
import com.aeneas.transaction.smart.script.ScriptCompiler
import org.scalacheck.Gen
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class BlockchainUpdaterNFTTest
    extends PropSpec
    with PropertyChecks
    with DomainScenarioDrivenPropertyCheck
    with Matchers
    with EitherMatchers
    with TransactionGen
    with BlocksTransactionsHelpers
    with NoShrink {

  property("nft list should be consistent with transfer") {
    forAll(Preconditions.nftTransfer()) {
      case (issue, Seq(firstAccount, secondAccount), Seq(genesisBlock, issueBlock, keyBlock, postBlock), Seq(microBlock)) =>
        withDomain(settingsWithFeatures(BlockchainFeatures.NG, BlockchainFeatures.ReduceNFTFee)) { d =>
          d.blockchainUpdater.processBlock(genesisBlock) should beRight
          d.blockchainUpdater.processBlock(issueBlock) should beRight
          d.blockchainUpdater.processBlock(keyBlock) should beRight

          d.nftList(firstAccount).map(_._1.id) shouldBe Seq(issue.id())
          d.nftList(secondAccount) shouldBe Nil

          d.blockchainUpdater.processMicroBlock(microBlock) should beRight
          d.nftList(firstAccount) shouldBe Nil
          d.nftList(secondAccount).map(_._1.id) shouldBe Seq(issue.id())

          d.blockchainUpdater.processBlock(postBlock) should beRight
          d.nftList(firstAccount) shouldBe Nil
          d.nftList(secondAccount).map(_._1.id) shouldBe Seq(issue.id())
        }
    }
  }

  property("nft list should be consistent with invokescript") {
    forAll(Preconditions.nftInvokeScript()) {
      case (issue, Seq(firstAccount, secondAccount), Seq(genesisBlock, issueBlock, keyBlock, postBlock), Seq(microBlock)) =>
        withDomain(
          settingsWithFeatures(
            BlockchainFeatures.NG,
            BlockchainFeatures.ReduceNFTFee,
            BlockchainFeatures.SmartAccounts,
            BlockchainFeatures.Ride4DApps
          )
        ) { d =>
          d.blockchainUpdater.processBlock(genesisBlock) should beRight
          d.blockchainUpdater.processBlock(issueBlock) should beRight
          d.blockchainUpdater.processBlock(keyBlock) should beRight

          d.nftList(firstAccount).map(_._1.id) shouldBe Seq(issue.id())
          d.nftList(secondAccount) shouldBe Nil

          d.blockchainUpdater.processMicroBlock(microBlock) should beRight
          d.nftList(firstAccount) shouldBe Nil
          d.nftList(secondAccount).map(_._1.id) shouldBe Seq(issue.id())

          d.blockchainUpdater.processBlock(postBlock) should beRight
          d.nftList(firstAccount) shouldBe Nil
          d.nftList(secondAccount).map(_._1.id) shouldBe Seq(issue.id())
        }
    }
  }

  private[this] object Preconditions {
    import UnsafeBlocks._

    def nftTransfer(): Gen[(IssueTransaction, Seq[Address], Seq[Block], Seq[MicroBlock])] = {
      for {
        richAccount   <- accountGen
        secondAccount <- accountGen
        blockTime = ntpNow
        issue    <- QuickTX.nftIssue(richAccount, Gen.const(blockTime))
        transfer <- QuickTX.transferAsset(IssuedAsset(issue.assetId), richAccount, secondAccount.toAddress, 1, Gen.const(blockTime))
      } yield {
        val genesisBlock = unsafeBlock(
          reference = randomSig,
          txs = Seq(GenesisTransaction.create(richAccount.toAddress, diffs.ENOUGH_AMT, 0).explicitGet()),
          signer = TestBlock.defaultSigner,
          version = 3.toByte,
          timestamp = 0
        )

        val issueBlock = unsafeBlock(
          genesisBlock.signature,
          Seq(issue),
          richAccount,
          3.toByte,
          blockTime
        )

        val (keyBlock, microBlocks) = unsafeChainBaseAndMicro(
          totalRefTo = issueBlock.signature,
          base = Seq(),
          micros = Seq(Seq(transfer)),
          signer = richAccount,
          version = 3.toByte,
          blockTime
        )

        val (postBlock, _) = unsafeChainBaseAndMicro(
          totalRefTo = microBlocks.last.totalResBlockSig,
          base = Seq(),
          micros = Seq(),
          signer = richAccount,
          version = 3.toByte,
          blockTime
        )
        (issue, Seq(richAccount.toAddress, secondAccount.toAddress), Seq(genesisBlock, issueBlock, keyBlock, postBlock), microBlocks)
      }
    }

    def nftInvokeScript(): Gen[(IssueTransaction, Seq[Address], Seq[Block], Seq[MicroBlock])] = {
      for {
        richAccount   <- accountGen
        secondAccount <- accountGen
        blockTime = ntpNow
        issue <- QuickTX.nftIssue(richAccount, Gen.const(blockTime))
        setScript <- {
          val scriptText =
            s"""
               |{-# STDLIB_VERSION 3 #-}
               |{-# CONTENT_TYPE DAPP #-}
               |{-# SCRIPT_TYPE ACCOUNT #-}
               |
               |@Callable(i)
               |func nftTransfer() = {
               |    let pmt = i.payment.extract()
               |    TransferSet([
               |            ScriptTransfer(this, pmt.amount, pmt.assetId)
               |        ])
               |}
               |
               | @Verifier(t)
               | func verify() = {
               |  true
               | }
               |
               |
              """.stripMargin
          val (script, _) = ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()
          QuickTX.setScript(secondAccount, script, Gen.const(blockTime))
        }
        invokeScript <- {
          val fc = FUNCTION_CALL(FunctionHeader.User("nftTransfer"), Nil)
          QuickTX.invokeScript(
            richAccount,
            secondAccount.toAddress,
            fc,
            Seq(InvokeScriptTransaction.Payment(1, IssuedAsset(issue.assetId))),
            Gen.const(blockTime)
          )
        }
      } yield {
        val genesisBlock = unsafeBlock(
          reference = randomSig,
          txs = Seq(
            GenesisTransaction.create(richAccount.toAddress, diffs.ENOUGH_AMT, 0).explicitGet(),
            GenesisTransaction.create(secondAccount.toAddress, 1000000, 0).explicitGet()
          ),
          signer = TestBlock.defaultSigner,
          version = 3,
          timestamp = 0
        )

        val issueBlock = unsafeBlock(
          genesisBlock.signature,
          Seq(issue, setScript),
          richAccount,
          3,
          blockTime
        )

        val (keyBlock, microBlocks) = unsafeChainBaseAndMicro(
          totalRefTo = issueBlock.signature,
          base = Seq(),
          micros = Seq(Seq(invokeScript)),
          signer = richAccount,
          version = 3,
          blockTime
        )

        val (postBlock, _) = unsafeChainBaseAndMicro(
          totalRefTo = microBlocks.last.totalResBlockSig,
          base = Seq(),
          micros = Seq(),
          signer = richAccount,
          version = 3,
          blockTime
        )
        (issue, Seq(richAccount.toAddress, secondAccount.toAddress), Seq(genesisBlock, issueBlock, keyBlock, postBlock), microBlocks)
      }
    }
  }
}
