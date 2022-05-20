package com.aeneas.it.sync.transactions

import com.aeneas.account.AddressScheme
import com.aeneas.api.http.ApiError.{CustomValidationError, InvalidName, NonPositiveAmount, TooBigArrayAllocation}
import com.aeneas.it.api.IssueTransactionInfo
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.it.sync._
import org.scalatest.prop.TableDrivenPropertyChecks

class IssueTransactionSuite extends BaseTransactionSuite with TableDrivenPropertyChecks {
  test("asset issue changes issuer's asset balance; issuer's ash balance is decreased by fee") {
    for (v <- issueTxSupportedVersions) {
      val assetName        = "myasset"
      val assetDescription = "my asset description"
      val (balance1, eff1) = miner.accountBalances(firstAddress)

      val issueTx =
        sender
          .issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = true, issueFee, version = v, script = scriptText(v))
      nodes.waitForHeightAriseAndTxPresent(issueTx.id)
      if (v > 2) {
        issueTx.chainId shouldBe Some(AddressScheme.current.chainId)
        sender.transactionInfo[IssueTransactionInfo](issueTx.id).chainId shouldBe Some(AddressScheme.current.chainId)
      }

      miner.assertBalances(firstAddress, balance1 - issueFee, eff1 - issueFee)
      miner.assertAssetBalance(firstAddress, issueTx.id, someAssetAmount)
    }
  }

  test("Able to create asset with the same name") {
    for (v <- issueTxSupportedVersions) {
      val assetName        = "myasset1"
      val assetDescription = "my asset description 1"
      val (balance1, eff1) = miner.accountBalances(firstAddress)

      val issuedAssetId =
        sender
          .issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = false, issueFee, version = v, script = scriptText(v))
          .id
      nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

      val issuedAssetIdSameAsset =
        sender
          .issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = true, issueFee, version = v, script = scriptText(v))
          .id
      nodes.waitForHeightAriseAndTxPresent(issuedAssetIdSameAsset)

      miner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
      miner.assertBalances(firstAddress, balance1 - 2 * issueFee, eff1 - 2 * issueFee)
    }
  }

  test("Not able to create asset when insufficient funds") {
    for (v <- issueTxSupportedVersions) {
      val assetName = "myasset"
      val assetDescription = "my asset description"
      val eff1 = miner.accountBalances(firstAddress)._2
      val bigAssetFee = eff1 + 1.waves

      assertApiError(sender.issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = false, bigAssetFee, version = v)) { error =>
        error.message should include("Accounts balance errors")
      }
    }
  }

  val invalidScript =
    Table(
      ("script", "error"),
      ("base64:AQa3b8tZ", "Invalid checksum"),
      ("base64:", "Can't parse empty script bytes"),
      ("base64:AA==", "Illegal length of script: 1"),
      ("base64:AAQB", "Invalid content type of script: 4"),
      ("base64:AAEF", "Invalid version of script: 5"),
      ("base64:CAEF", "Invalid version of script: 8")
    )

  forAll(invalidScript) { (script: String, error: String) =>
    test(s"Try to put incorrect script=$script") {
      for (v <- issueTxSupportedVersions) {
        val assetName = "myasset"
        val assetDescription = "my asset description"

        assertApiError(
          sender.issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = false, issueFee, script = Some(script), version = v),
          CustomValidationError(s"ScriptParseError($error)")
        )
      }
    }
  }

  val invalidAssetValue =
    Table(
      ("assetVal", "decimals", "message"),
      (0L, 2, NonPositiveAmount("0 of assets").assertive(true)),
      (1L, 9, TooBigArrayAllocation.assertive()),
      (-1L, 1, NonPositiveAmount("-1 of assets").assertive(true)),
      (1L, -1, TooBigArrayAllocation.assertive())
    )

  forAll(invalidAssetValue) { (assetVal: Long, decimals: Int, message: AssertiveApiError) =>
    test(s"Not able to create asset total token='$assetVal', decimals='$decimals' ") {
      for (v <- issueTxSupportedVersions) {
        val assetName = "myasset2"
        val assetDescription = "my asset description 2"
        val decimalBytes: Byte = decimals.toByte
        assertApiError(
          sender.issue(firstAddress, assetName, assetDescription, assetVal, decimalBytes, reissuable = false, issueFee, version = v),
          message
        )
      }
    }
  }

  test(s"Not able to create asset without name") {
    for (v <- issueTxSupportedVersions) {
      assertApiError(sender.issue(firstAddress, null, null, someAssetAmount, 2, reissuable = false, issueFee, version = v)) { error =>
        error.message should include regex "failed to parse json message"
        error.json.fields.map(_._1) should contain("validationErrors")
      }
    }
  }

  val invalid_assets_names =
    Table(
      "abc",
      "UpperCaseAssetCoinTest",
      "~!|#$%^&*()_+=\";:/?><|\\][{}"
    )

  forAll(invalid_assets_names) { assetName: String =>
    test(s"Not able to create asset named $assetName") {
      for (v <- issueTxSupportedVersions) {
        assertApiError(
          sender.issue(firstAddress, assetName, assetName, someAssetAmount, 2, reissuable = false, issueFee, version = v),
          InvalidName
        )
      }
    }
  }

  def scriptText(version: Int): Option[String] = version match {
    case 2 => Some(scriptBase64)
    case _ => None
  }

}
