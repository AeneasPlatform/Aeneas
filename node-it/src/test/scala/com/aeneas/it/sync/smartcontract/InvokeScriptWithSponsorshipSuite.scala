package com.aeneas.it.sync.smartcontract

import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync.{minFee, smartMinFee}
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.smart.InvokeScriptTransaction.Payment
import com.aeneas.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeScriptWithSponsorshipSuite extends BaseTransactionSuite with CancelAfterFailure {
  val estimator = ScriptEstimatorV2

  private val dApp   = firstAddress
  private val caller = secondAddress

  val quantity: Long          = 10000
  val halfQuantity: Long      = quantity / 2
  var dAppAsset: String       = ""
  var callerAsset: String     = ""
  var smartAsset: String      = ""
  var dAppInitBalance: Long   = 0
  var callerInitBalance: Long = 0

  test("_send waves to dApp and caller accounts") {
    sender.transfer(sender.address, dApp, 5.waves, minFee, waitForTx = true).id
    sender.transfer(sender.address, caller, 5.waves, minFee, waitForTx = true).id

  }

  test("_issue and transfer assets") {
    dAppAsset = sender.issue(dApp, "dApp", "d", quantity, 0, waitForTx = true).id
    callerAsset = sender.issue(caller, "caller", "c", quantity, 0, waitForTx = true).id
    val script = Some(ScriptCompiler.compile("true", estimator).explicitGet()._1.bytes.value.base64)
    smartAsset = sender.issue(dApp, "Smart", "s", quantity, 0, script = script, waitForTx = true).id

    sender.transfer(dApp, caller, halfQuantity, minFee, Some(dAppAsset), waitForTx = true).id
    sender.transfer(caller, dApp, halfQuantity, minFee, Some(callerAsset), waitForTx = true).id
    sender.transfer(dApp, caller, halfQuantity, smartMinFee, Some(smartAsset), waitForTx = true).id
  }

  test("_enable sponsorship") {
    sender.sponsorAsset(dApp, dAppAsset, 1, waitForTx = true).id
    sender.sponsorAsset(caller, callerAsset, 1, waitForTx = true).id
  }

  test("_set scripts to dApp and caller account") {
    val dAppScript = ScriptCompiler
      .compile(
        s"""
          |{-# STDLIB_VERSION 3 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |
          |let dAppAsset = base58'$dAppAsset'
          |let callerAsset = base58'$callerAsset'
          |let smartAsset = base58'$smartAsset'
          |
          |@Callable(i)
          |func payCallerGetDAppAsset() = {
          |  if (isDefined(i.payment) && extract(i.payment).assetId == callerAsset) then
          |    TransferSet([
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset),
          |      ScriptTransfer(i.caller, 1, dAppAsset)
          |    ])
          |  else throw("need payment in callerAsset " + toBase58String(callerAsset))
          |}
          |
          |@Callable(i)
          |func spendMaxFee() = {
          |  if (isDefined(i.payment) && extract(i.payment).assetId == smartAsset) then
          |    TransferSet([
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset),
          |      ScriptTransfer(i.caller, 1, smartAsset)
          |    ])
          |  else throw("need payment in smartAsset " + toBase58String(smartAsset))
          |}
        """.stripMargin,
        estimator
      )
      .explicitGet()
      ._1
    sender.setScript(dApp, Some(dAppScript.bytes().base64), waitForTx = true).id

    val callerScript = ScriptCompiler
      .compile(
        s"""
          |{-# STDLIB_VERSION 3 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |
          |@Verifier(tx)
          |func verify() = {
          |  let callerAsset = base58'$callerAsset'
          |  let smartAsset = base58'$smartAsset'
          |  match (tx) {
          |    case tx:InvokeScriptTransaction =>
          |      let pay = extract(tx.payment)
          |      pay.assetId == callerAsset || pay.assetId == smartAsset
          |    case _ => false
          |  }
          |}
        """.stripMargin,
        estimator
      )
      .explicitGet()
      ._1
    sender.setScript(caller, Some(callerScript.bytes().base64), waitForTx = true).id

    val dAppScriptInfo = sender.addressScriptInfo(dApp)
    dAppScriptInfo.script.isEmpty shouldBe false
    dAppScriptInfo.scriptText.isEmpty shouldBe false
    dAppScriptInfo.script.get.startsWith("base64:") shouldBe true

    val smartCallerScriptInfo = sender.addressScriptInfo(caller)
    smartCallerScriptInfo.script.isEmpty shouldBe false
    smartCallerScriptInfo.scriptText.isEmpty shouldBe false
    smartCallerScriptInfo.script.get.startsWith("base64:") shouldBe true
  }

  test("required fee in sponsored assets considers scripts count") {
    dAppInitBalance = sender.accountBalances(dApp)._1
    callerInitBalance = sender.accountBalances(caller)._1

    val paymentAmount  = 1
    val feeAmount      = 9
    val smartFeeAmount = 53

    assertBadRequestAndMessage(
      sender.invokeScript(
        caller,
        dApp,
        Some("payCallerGetDAppAsset"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(callerAsset).get))),
        fee = feeAmount - 1,
        feeAssetId = Some(dAppAsset)
      ),
      s"does not exceed minimal value of 900000 WAVES or $feeAmount"
    )
    val tx = sender
      .invokeScript(
        caller,
        dApp,
        Some("spendMaxFee"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(smartAsset).get))),
        fee = smartFeeAmount - 1,
        feeAssetId = Some(dAppAsset),
        waitForTx = true
      )
      ._1
      .id
    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include("does not exceed minimal value of 5300000 WAVES")

    sender
      .invokeScript(
        caller,
        dApp,
        Some("payCallerGetDAppAsset"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(callerAsset).get))),
        fee = feeAmount,
        feeAssetId = Some(dAppAsset),
        waitForTx = true
      )
      ._1
      .id
    sender
      .invokeScript(
        caller,
        dApp,
        Some("spendMaxFee"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(smartAsset).get))),
        fee = smartFeeAmount,
        feeAssetId = Some(dAppAsset),
        waitForTx = true
      )
      ._1
      .id

    sender.assetBalance(dApp, dAppAsset).balance shouldBe halfQuantity + (feeAmount - 10) + smartFeeAmount + (smartFeeAmount - 1)
    sender.assetBalance(dApp, callerAsset).balance shouldBe halfQuantity + paymentAmount
    sender.accountBalances(dApp)._1 shouldBe dAppInitBalance - 0.009.waves - 0.053.waves - 0.052.waves

    sender.assetBalance(caller, dAppAsset).balance shouldBe halfQuantity + (-feeAmount + 10) - smartFeeAmount - (smartFeeAmount - 1)
    sender.assetBalance(caller, callerAsset).balance shouldBe halfQuantity - paymentAmount
    sender.accountBalances(caller)._1 shouldBe callerInitBalance
  }

  test("dApp caller is dApp address") {
    val paymentAmount = 1
    val feeAmount     = 9

    val dAppAssetBalance = sender.assetBalance(dApp, dAppAsset).balance
    val dAppWavesBalance = sender.accountBalances(dApp)._1

    sender
      .invokeScript(
        dApp,
        dApp,
        Some("payCallerGetDAppAsset"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(callerAsset).get))),
        fee = feeAmount,
        feeAssetId = Some(dAppAsset),
        waitForTx = true
      )
      ._1
      .id

    sender.assetBalance(dApp, dAppAsset).balance shouldBe dAppAssetBalance
    sender.accountBalances(dApp)._1 shouldBe dAppWavesBalance - 0.009.waves
  }

}
