package com.aeneas.it.sync.smartcontract

import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync.{issueFee, minFee, smartFee, smartMinFee}
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.smart.InvokeScriptTransaction.Payment
import com.aeneas.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeScriptWithSmartAccountAndAssetSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val estimator = ScriptEstimatorV2

  private val dApp        = firstAddress
  private val caller      = secondAddress
  private val smartCaller = thirdAddress

  var asset1: String = ""
  var asset2: String = ""
  var asset3: String = ""

  test("_send ash to dApp and caller accounts") {
    val dAppTransferId        = sender.transfer(sender.address, dApp, 5.waves, minFee).id
    val callerTransferId      = sender.transfer(sender.address, caller, 5.waves, minFee).id
    val smartCallerTransferId = sender.transfer(sender.address, smartCaller, 5.waves, minFee).id

    nodes.waitForHeightAriseAndTxPresent(smartCallerTransferId)
    nodes.waitForTransaction(callerTransferId)
    nodes.waitForTransaction(dAppTransferId)
  }

  test("_issue and transfer smart assets between dApp and caller") {
    asset1 = sender
      .issue(
        dApp,
        "Asset1",
        "test asset",
        1500,
        0,
        reissuable = true,
        issueFee,
        script = Some(
          ScriptCompiler
            .compile(
              s"""
           |match tx {
           |  case tx:TransferTransaction => tx.amount > 10
           |  case _ => false
           |}""".stripMargin,
              estimator
            )
            .explicitGet()
            ._1
            .bytes
            .value
            .base64
        )
      )
      .id

    asset2 = sender
      .issue(
        dApp,
        "Asset2",
        "test asset",
        1500,
        0,
        reissuable = true,
        issueFee,
        script = Some(
          ScriptCompiler
            .compile(
              s"""
           |{-# STDLIB_VERSION 3 #-}
           |match tx {
           |  case tx:InvokeScriptTransaction => extract(tx.payment).amount > 10
           |  case _:TransferTransaction => true
           |  case _ => false
           |}""".stripMargin,
              estimator
            )
            .explicitGet()
            ._1
            .bytes
            .value
            .base64
        )
      )
      .id

    asset3 = sender
      .issue(
        dApp,
        "Asset3",
        "test asset",
        1500,
        0,
        reissuable = true,
        issueFee,
        script = Some(
          ScriptCompiler
            .compile(
              s"""
                 |{-# STDLIB_VERSION 3 #-}
                 |match tx {
                 |  case tx:TransferTransaction => tx.amount > 20
                 |  case _ => false
                 |}""".stripMargin,
              estimator
            )
            .explicitGet()
            ._1
            .bytes
            .value
            .base64
        )
      )
      .id

    nodes.waitForHeightAriseAndTxPresent(asset3)
    nodes.waitForHeightAriseAndTxPresent(asset2)
    sender.waitForTransaction(asset1)

    val asset1ToCallerId = sender.transfer(dApp, caller, 500, smartMinFee, Some(asset1)).id
    val asset2ToCallerId = sender.transfer(dApp, caller, 500, smartMinFee, Some(asset2)).id
    val asset3ToCallerId = sender.transfer(dApp, caller, 500, smartMinFee, Some(asset3)).id
    val asset1ToSmartId  = sender.transfer(dApp, smartCaller, 500, smartMinFee, Some(asset1)).id
    val asset2ToSmartId  = sender.transfer(dApp, smartCaller, 500, smartMinFee, Some(asset2)).id
    val asset3ToSmartId  = sender.transfer(dApp, smartCaller, 500, smartMinFee, Some(asset3)).id
    nodes.waitForHeightAriseAndTxPresent(asset2ToSmartId)
    nodes.waitForHeightAriseAndTxPresent(asset3ToSmartId)
    sender.waitForTransaction(asset1ToCallerId)
    sender.waitForTransaction(asset2ToCallerId)
    sender.waitForTransaction(asset3ToCallerId)
    sender.waitForTransaction(asset1ToSmartId)
  }

  test("_set scripts to dApp and smartCaller account") {
    val dAppScript = ScriptCompiler
      .compile(
        s"""
          |{-# STDLIB_VERSION 3 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |
          |let asset1 = base58'$asset1'
          |let asset2 = base58'$asset2'
          |let asset3 = base58'$asset3'
          |
          |@Callable(i)
          |func payAsset1GetAsset2() = {
          |  let pay = extract(i.payment)
          |  if (pay.assetId == asset1 && pay.amount > 15) then
          |    TransferSet([ScriptTransfer(i.caller, 15, asset2)])
          |  else throw("need payment in 15+ tokens of asset1 " + toBase58String(asset1))
          |}
          |
          |@Callable(i)
          |func payAsset2GetAsset1() = {
          |  let pay = extract(i.payment)
          |  if (pay.assetId == asset2 && pay.amount > 15) then
          |    TransferSet([ScriptTransfer(i.caller, 15, asset1)])
          |  else {
          |    if (${"sigVerify(base58'', base58'', base58'') ||" * 16} true)
          |    then
          |       throw("need payment in 15+ tokens of asset2 " + toBase58String(asset2))
          |    else
          |       throw("unexpected")
          |  }
          |}
          |@Callable(i)
          |func payAsset2GetAsset3() = {
          |  let pay = extract(i.payment)
          |  if (pay.assetId == asset2 && pay.amount > 15) then
          |    TransferSet([ScriptTransfer(i.caller, 15, asset3)])
          |  else throw("need payment in 15+ tokens of asset2 " + toBase58String(asset2))
          |}
          |
          |@Callable(i)
          |func get10ofAsset1() = {
          |  TransferSet([ScriptTransfer(i.caller, 10, asset1)])
          |}
          |
          |@Callable(i)
          |func spendMaxFee() = {
          |  if (extract(i.payment).assetId == asset2) then
          |    TransferSet([
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1),
          |      ScriptTransfer(i.caller, 11, asset1)
          |    ])
          |  else throw("need payment in asset2 " + toBase58String(asset2))
          |}
          |
          |@Callable(i)
          |func justWriteData() = {
          |  WriteSet([DataEntry("a", "a")])
          |}
        """.stripMargin,
        estimator
      )
      .explicitGet()
      ._1
    val dAppSetScriptTxId = sender.setScript(dApp, Some(dAppScript.bytes().base64)).id

    val smartCallerScript = ScriptCompiler
      .compile(
        """
          |{-# STDLIB_VERSION 3 #-}
          |{-# CONTENT_TYPE DAPP #-}
          |
          |@Verifier(tx)
          |func verify() = {
          |  match (tx) {
          |    case tx:InvokeScriptTransaction =>
          |      if (isDefined(tx.payment)) then
          |        extract(tx.payment).amount > 12
          |      else true
          |    case _ => false
          |  }
          |}
        """.stripMargin,
        estimator
      )
      .explicitGet()
      ._1
    val smartCallerSetScriptTxId = sender.setScript(smartCaller, Some(smartCallerScript.bytes().base64)).id

    nodes.waitForHeightAriseAndTxPresent(smartCallerSetScriptTxId)
    sender.waitForTransaction(dAppSetScriptTxId)

    val dAppScriptInfo = sender.addressScriptInfo(dApp)
    dAppScriptInfo.script.isEmpty shouldBe false
    dAppScriptInfo.scriptText.isEmpty shouldBe false
    dAppScriptInfo.script.get.startsWith("base64:") shouldBe true
    val smartCallerScriptInfo = sender.addressScriptInfo(smartCaller)
    smartCallerScriptInfo.script.isEmpty shouldBe false
    smartCallerScriptInfo.scriptText.isEmpty shouldBe false
    smartCallerScriptInfo.script.get.startsWith("base64:") shouldBe true
  }

  test("invoke by smart account requires just 1 extra fee") {
    assertBadRequestAndMessage(
      sender.invokeScript(
        smartCaller,
        dApp,
        Some("justWriteData"),
        fee = 0.00899999.waves
      ),
      s"does not exceed minimal value of 900000 ASH"
    )
  }

  test("max fee is 0.053 Ash (0.005 + extraFee(1 smart caller + 1 payment + 10 transfers))") {
    val paymentAmount = 20

    val tx = sender
      .invokeScript(
        smartCaller,
        dApp,
        Some("spendMaxFee"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
        fee = 0.05299999.waves,
        waitForTx = true
      )
      ._1
      .id

    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include(
      "with 12 total scripts invoked does not exceed minimal value of 5300000"
    )

    val invokeScriptTxId = sender
      .invokeScript(
        smartCaller,
        dApp,
        Some("spendMaxFee"),
        payment = Seq(Payment(paymentAmount, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
        fee = 5300000
      )
      ._1
      .id
    nodes.waitForHeightAriseAndTxPresent(invokeScriptTxId)

    sender.debugStateChanges(invokeScriptTxId).stateChanges.get.error shouldBe empty
  }

  test("can't invoke with insufficient payment for @Verifier") {
    val amountLessThanVerifierLimit = 12

    assertBadRequestAndMessage(
      sender
        .invokeScript(
          smartCaller,
          dApp,
          Some("get10ofAsset1"),
          payment = Seq(Payment(amountLessThanVerifierLimit, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
          fee = smartMinFee + smartFee + smartFee // scripted asset + dApp
        )
        ._1
        .id,
      "Transaction is not allowed by account-script"
    )
  }

  test("can't invoke with small fee for both smart assets") {
    val amountGreaterThanAccountScriptLimit = 20

    assertBadRequestAndMessage(
      sender
        .invokeScript(
          caller,
          dApp,
          Some("payAsset2GetAsset1"),
          payment = Seq(Payment(amountGreaterThanAccountScriptLimit, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
          fee = smartMinFee
        )
        ._1
        .id,
      "does not exceed minimal value of 900000 ASH"
    )
  }

  test("can't invoke with small fee for one of smart assets") {
    val amountGreaterThanAccountScriptLimit = 20

    val tx = sender
      .invokeScript(
        caller,
        dApp,
        Some("payAsset2GetAsset1"),
        payment = Seq(Payment(amountGreaterThanAccountScriptLimit, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
        fee = smartMinFee + smartFee,
        waitForTx = true
      )
      ._1
      .id
    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include(
      "with 2 total scripts invoked does not exceed minimal value of 1300000"
    )
  }

  test("can invoke a function with enough payment and fee") {
    val amountGreaterThanAccountScriptLimit = 20

    val invokeScriptId = sender
      .invokeScript(
        caller,
        dApp,
        Some("payAsset2GetAsset1"),
        payment = Seq(Payment(amountGreaterThanAccountScriptLimit, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
        fee = smartMinFee + smartFee + smartFee
      )
      ._1
      .id

    nodes.waitForHeightAriseAndTxPresent(invokeScriptId)
  }

  test("can't invoke with payment if asset script disallows InvokeScript") {
    val amountGreaterThanDAppScriptLimit = 16

    val tx = sender
      .invokeScript(
        caller,
        dApp,
        Some("payAsset1GetAsset2"),
        payment = Seq(Payment(amountGreaterThanDAppScriptLimit, IssuedAsset(ByteStr.decodeBase58(asset1).get))),
        fee = smartMinFee + smartFee + smartFee,
        waitForTx = true
      )
      ._1
      .id
    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include("Transaction is not allowed by script of the asset")
  }

  test("can't invoke a function with payment less than dApp script's limit") {
    val amountLessThanDAppScriptLimit = 15

    val tx = sender
      .invokeScript(
        caller,
        dApp,
        Some("payAsset2GetAsset1"),
        payment = Seq(Payment(amountLessThanDAppScriptLimit, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
        fee = smartMinFee + smartFee + smartFee,
        waitForTx = true
      )
      ._1
      .id
    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include(s"need payment in 15+ tokens of asset2 $asset2")
  }

  test("can't invoke a function with payment less than asset script's limit") {
    val amountGreaterThanDAppScriptLimit = 16

    val tx = sender
      .invokeScript(
        caller,
        dApp,
        Some("payAsset2GetAsset3"),
        payment = Seq(Payment(amountGreaterThanDAppScriptLimit, IssuedAsset(ByteStr.decodeBase58(asset2).get))),
        fee = smartMinFee + smartFee + smartFee,
        waitForTx = true
      )
      ._1
      .id
    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include("Transaction is not allowed by script of the asset")
  }

  test("can't invoke a function that transfers less than asset script's limit") {
    val tx = sender.invokeScript(caller, dApp, Some("get10ofAsset1"), fee = smartMinFee + smartFee, waitForTx = true)._1.id
    sender.debugStateChanges(tx).stateChanges.get.error.get.text should include("Transaction is not allowed by script of the asset")
  }

}
