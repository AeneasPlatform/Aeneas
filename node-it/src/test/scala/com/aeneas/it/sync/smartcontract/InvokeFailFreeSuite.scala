package com.aeneas.it.sync.smartcontract

import akka.http.scaladsl.model.StatusCodes
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.api.TransactionInfo
import com.aeneas.it.sync.setScriptFee
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.lang.v1.compiler.Terms.CONST_BOOLEAN
import com.aeneas.lang.v1.estimator.v3.ScriptEstimatorV3
import com.aeneas.transaction.smart.script.ScriptCompiler

class InvokeFailFreeSuite extends BaseTransactionSuite {
  private val dApp = firstAddress

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    val script =
      compile(
        s"""
          | {-# STDLIB_VERSION 4 #-}
          | {-# CONTENT_TYPE DAPP #-}
          |
          | @Callable(inv)
          | func default(reject: Boolean) = {
          |   let check =
          |     if (reject)
          |       then
          |         ${"sigVerify(base58'', base58'', base58'') ||" * 7} true
          |       else
          |         ${"sigVerify(base58'', base58'', base58'') ||" * 8} false
          |
          |   if (check)
          |     then throw("rejected")
          |     else throw("failed")
          | }
        """.stripMargin
      )

    val setScriptId = sender.setScript(dApp, Some(script), setScriptFee, waitForTx = true).id
    sender.transactionInfo[TransactionInfo](setScriptId).script.get.startsWith("base64:") shouldBe true

    val scriptInfo = sender.addressScriptInfo(dApp)
    scriptInfo.scriptText.isEmpty shouldBe false
    scriptInfo.script.get.startsWith("base64:") shouldBe true
  }

  private def compile(scriptText: String): String =
    ScriptCompiler.compile(scriptText, ScriptEstimatorV3).explicitGet()._1.bytes().base64

  test("fail invoke script transaction if complexity exceeds fail-free limit") {
    val tx = sender.invokeScript(
      caller = sender.address,
      dappAddress = dApp,
      func = Some("default"),
      args = List(CONST_BOOLEAN(false)),
      waitForTx = true
    )
    sender.transactionStatus(Seq(tx._1.id)).head.status shouldBe "confirmed"
    sender.debugStateChanges(tx._1.id).stateChanges.get.error.get.text should include("failed")
  }

  test("reject invoke script transaction if complexity conforms fail-free limit") {
    val tx = sender.invokeScript(
      caller = sender.address,
      dappAddress = dApp,
      func = Some("default"),
      args = List(CONST_BOOLEAN(true))
    )
    sender.waitForHeight(sender.height + 1)
    sender.transactionStatus(Seq(tx._1.id)).head.status shouldBe "not_found"
    assertApiErrorRaised(
      sender.debugStateChanges(tx._1.id).stateChanges,
      StatusCodes.NotFound.intValue
    )
  }
}
