package com.aeneas.it.sync.smartcontract

import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.lang.v1.compiler.Terms.CONST_STRING
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.state.IntegerDataEntry
import com.aeneas.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeWithTransferSmartassetSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val estimator = ScriptEstimatorV2

  private val dApp      = firstAddress
  private val callerAcc = secondAddress
  private val issuerAcc = thirdAddress

  private val accScript = ScriptCompiler
    .compile(
      """
        |{-# STDLIB_VERSION 4 #-}
        |{-# CONTENT_TYPE DAPP #-}
        |{-# SCRIPT_TYPE ACCOUNT #-}
        |
        |@Callable(inv)
        |func f(assetId: String) = {
        |    if (this.getInteger("y") == 1) then {
        |     [ScriptTransfer(inv.caller, 1, fromBase58String(assetId))]
        |    } else
        |       nil
        |}
                                                """.stripMargin,
      estimator
    ).explicitGet()._1.bytes().base64

  private val assetScript = ScriptCompiler
    .compile(
      """
        |{-# STDLIB_VERSION 4 #-}
        |{-# CONTENT_TYPE EXPRESSION #-}
        |{-# SCRIPT_TYPE ASSET #-}
        |
        |this.issuer.getInteger("x") == 1
                                        """.stripMargin,
      estimator
    ).explicitGet()._1.bytes().base64

  var issuedAssetId = ""

  test("prereqisetes: issue asset and set dapp") {
    val issuerData = List(IntegerDataEntry("x", 1))
    sender.putData(issuerAcc, issuerData, 0.1.waves, waitForTx = true)

    val dAppData = List(IntegerDataEntry("y", 1))
    sender.putData(dApp, dAppData, 0.1.waves, waitForTx = true)

    issuedAssetId = sender.issue(thirdAddress, "some", "asset", someAssetAmount, script = Some(assetScript), waitForTx = true).id
    sender.transfer(issuerAcc, dApp, someAssetAmount, smartMinFee, Some(issuedAssetId), waitForTx = true)
    sender.setScript(firstAddress, Some(accScript), setScriptFee, waitForTx = true)
  }

  test("can make transfer") {
    val callerBalance = sender.assetBalance(callerAcc, issuedAssetId).balance
    val dAppBalance = sender.assetBalance(dApp, issuedAssetId).balance

    sender
      .invokeScript(
        callerAcc,
        dApp,
        Some("f"),
        args = List(CONST_STRING(issuedAssetId).explicitGet()),
        Seq.empty,
        smartMinFee + smartFee,
        None,
        waitForTx = true
      )
      ._1
      .id

    sender.assetBalance(callerAcc, issuedAssetId).balance shouldBe callerBalance + 1
    sender.assetBalance(dApp, issuedAssetId).balance shouldBe dAppBalance - 1
  }
}
