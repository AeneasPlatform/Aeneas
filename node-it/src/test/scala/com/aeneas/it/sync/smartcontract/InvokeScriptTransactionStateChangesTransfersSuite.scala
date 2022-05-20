package com.aeneas.it.sync.smartcontract

import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.lang.v1.compiler.Terms.CONST_LONG
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.smart.InvokeScriptTransaction.Payment
import com.aeneas.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class InvokeScriptTransactionStateChangesTransfersSuite extends BaseTransactionSuite with CancelAfterFailure {

  private val dApp               = firstAddress
  private val callerAndRecipient = secondAddress

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    val script = ScriptCompiler
      .compile(
        """
      |{-# STDLIB_VERSION 4 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |
      |@Callable(inv)
      |func sendToCaller(amount: Int) = [ScriptTransfer(inv.caller, amount, unit)]
      |
      """.stripMargin,
        ScriptEstimatorV2
      )
      .explicitGet()
      ._1
      .bytes()
      .base64
    sender.setScript(dApp, Some(script), setScriptFee, waitForTx = true)
  }

  test("payment value higher than transfer") {
    val pamentAmount   = 2
    val transferAmount = 1

    val invokeScriptTx = sender.invokeScript(
      callerAndRecipient,
      dApp,
      func = Some("sendToCaller"),
      args = List(CONST_LONG(transferAmount)),
      payment = Seq(Payment(pamentAmount, Waves)),
      fee = 1.waves,
      waitForTx = true
    )
    nodes.waitForHeightAriseAndTxPresent(invokeScriptTx._1.id)
    val txStateChanges = sender.debugStateChanges(invokeScriptTx._1.id)

    val transferCountOpt            = txStateChanges.stateChanges.map(_.transfers.size)
    val firstTransferAddrOpt        = txStateChanges.stateChanges.map(_.transfers.head.address)
    val firstTransferAssetAmountOpt = txStateChanges.stateChanges.map(_.transfers.head.amount)

    transferCountOpt shouldBe Some(1)
    firstTransferAddrOpt shouldBe Some(callerAndRecipient)
    firstTransferAssetAmountOpt shouldBe Some(transferAmount)
  }

  test("payment equal to transfer") {
    val pamentAmount   = 3
    val transferAmount = 3

    val invokeScriptTx = sender.invokeScript(
      callerAndRecipient,
      dApp,
      func = Some("sendToCaller"),
      args = List(CONST_LONG(transferAmount)),
      payment = Seq(Payment(pamentAmount, Waves)),
      fee = 1.waves,
      waitForTx = true
    )
    nodes.waitForHeightAriseAndTxPresent(invokeScriptTx._1.id)
    val txStateChanges = sender.debugStateChanges(invokeScriptTx._1.id)

    val transferCountOpt            = txStateChanges.stateChanges.map(_.transfers.size)
    val firstTransferAddrOpt        = txStateChanges.stateChanges.map(_.transfers.head.address)
    val firstTransferAssetAmountOpt = txStateChanges.stateChanges.map(_.transfers.head.amount)

    transferCountOpt shouldBe Some(1)
    firstTransferAddrOpt shouldBe Some(callerAndRecipient)
    firstTransferAssetAmountOpt shouldBe Some(transferAmount)
  }

  test("payment value lower than transfer") {
    val paymentAmount   = 1
    val transferAmount = 4

    val invokeScriptTx = sender.invokeScript(
      callerAndRecipient,
      dApp,
      func = Some("sendToCaller"),
      args = List(CONST_LONG(transferAmount)),
      payment = Seq(Payment(paymentAmount, Waves)),
      fee = 1.waves,
      waitForTx = true
    )
    nodes.waitForHeightAriseAndTxPresent(invokeScriptTx._1.id)
    val txStateChanges = sender.debugStateChanges(invokeScriptTx._1.id)

    val transferCountOpt            = txStateChanges.stateChanges.map(_.transfers.size)
    val firstTransferAddrOpt        = txStateChanges.stateChanges.map(_.transfers.head.address)
    val firstTransferAssetAmountOpt = txStateChanges.stateChanges.map(_.transfers.head.amount)

    transferCountOpt shouldBe Some(1)
    firstTransferAddrOpt shouldBe Some(callerAndRecipient)
    firstTransferAssetAmountOpt shouldBe Some(transferAmount)
  }

  test("zero transfer amount") {
    val paymentAmount  = 1
    val transferAmount = 0

    val invokeScriptTx = sender.invokeScript(
      callerAndRecipient,
      dApp,
      func = Some("sendToCaller"),
      args = List(CONST_LONG(transferAmount)),
      payment = Seq(Payment(paymentAmount, Waves)),
      fee = 1.waves,
      waitForTx = true
    )
    nodes.waitForHeightAriseAndTxPresent(invokeScriptTx._1.id)
    val txStateChanges = sender.debugStateChanges(invokeScriptTx._1.id)

    val transferCountOpt            = txStateChanges.stateChanges.map(_.transfers.size)
    val firstTransferAddrOpt        = txStateChanges.stateChanges.map(_.transfers.head.address)
    val firstTransferAssetAmountOpt = txStateChanges.stateChanges.map(_.transfers.head.amount)

    transferCountOpt shouldBe Some(1)
    firstTransferAddrOpt shouldBe Some(callerAndRecipient)
    firstTransferAssetAmountOpt shouldBe Some(transferAmount)
  }
}
