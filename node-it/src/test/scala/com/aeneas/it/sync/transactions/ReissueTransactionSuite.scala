package com.aeneas.it.sync.transactions

import com.typesafe.config.Config
import com.aeneas.account.AddressScheme
import com.aeneas.api.http.ApiError.StateCheckFailed
import com.aeneas.it.NodeConfigs
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.api.TransactionInfo
import com.aeneas.it.sync._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.transaction.assets.ReissueTransaction

class ReissueTransactionSuite extends BaseTransactionSuite {

  test("asset reissue changes issuer's asset balance; issuer's ash balance is decreased by fee") {
    for (v <- reissueTxSupportedVersions) {
      val (balance, effectiveBalance) = miner.accountBalances(firstAddress)

      val issuedAssetId = sender.issue(firstAddress, "name2", "description2", someAssetAmount, decimals = 2, reissuable = true, issueFee).id
      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)

      val reissueTx = sender.reissue(firstAddress, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueReducedFee, version = v)
      nodes.waitForHeightAriseAndTxPresent(reissueTx.id)
      if (v > 2) {
        reissueTx.chainId shouldBe Some(AddressScheme.current.chainId)
        sender.transactionInfo[TransactionInfo](reissueTx.id).chainId shouldBe Some(AddressScheme.current.chainId)
      }
      miner.assertBalances(firstAddress, balance - issueFee - reissueReducedFee, effectiveBalance - issueFee - reissueReducedFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, 2 * someAssetAmount)
    }

    miner.transactionsByAddress(firstAddress, limit = 100)
      .count(_._type == ReissueTransaction.typeId) shouldBe reissueTxSupportedVersions.length
  }

  test("can't reissue not reissuable asset") {
    for (v <- reissueTxSupportedVersions) {
      val (balance, effectiveBalance) = miner.accountBalances(firstAddress)

      val issuedAssetId = sender.issue(firstAddress, "name2", "description2", someAssetAmount, decimals = 2, reissuable = false, issueFee).id
      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)

      assertBadRequestAndMessage(
        sender.reissue(firstAddress, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueReducedFee, version = v),
        "Asset is not reissuable"
      )
      nodes.waitForHeightArise()

      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
    }
  }

  test("not able to reissue if cannot pay fee - less than required") {
    for (v <- reissueTxSupportedVersions) {
      val issuedAssetId = sender.issue(firstAddress, "name3", "description3", someAssetAmount, decimals = 2, reissuable = true, issueFee).id

      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

      assertApiError(sender.reissue(firstAddress, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueReducedFee - 1, version = v)) { error =>
        error.id shouldBe StateCheckFailed.Id
        error.message should include(s"Fee for ReissueTransaction (${reissueReducedFee - 1} in ASH) does not exceed minimal value of $reissueReducedFee ASH.")
      }
    }
  }

  test("not able to reissue if cannot pay fee - insufficient funds") {
    for (v <- reissueTxSupportedVersions) {
      val (balance, effectiveBalance) = miner.accountBalances(firstAddress)
      val reissueFee = effectiveBalance + 1.waves

      val issuedAssetId = sender.issue(firstAddress, "name4", "description4", someAssetAmount, decimals = 2, reissuable = true, issueFee).id

      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

      assertBadRequestAndMessage(
        sender.reissue(firstAddress, issuedAssetId, someAssetAmount, reissuable = true, fee = reissueFee, version = v),
        "Accounts balance errors"
      )
      nodes.waitForHeightArise()

      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
      miner.assertBalances(firstAddress, balance - issueFee, effectiveBalance - issueFee)
    }
  }

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .withDefault(1)
      .withSpecial(_.nonMiner)
      .buildNonConflicting()
}
