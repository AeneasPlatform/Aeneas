package com.aeneas.it.sync.smartcontract

import com.typesafe.config.Config
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.NodeConfigs
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.api.TransactionInfo
import com.aeneas.it.sync._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.smart.script.ScriptCompiler

class SetScriptBodyBytesByteVectorSuite extends BaseTransactionSuite {
  private def compile(scriptText: String) =
    ScriptCompiler.compile(scriptText, ScriptEstimatorV2).explicitGet()._1.bytes().base64

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .withDefault(1)
      .buildNonConflicting()

  private val expectedBodyBytesSize = 32815

  private val verifierV3 =
    compile(
      s"""
         |{-# STDLIB_VERSION 3 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |
         | match tx {
         |    case sstx: SetScriptTransaction =>
         |      sstx.bodyBytes.size() == $expectedBodyBytesSize
         |
         |   case _ =>
         |      throw("unexpected")
         | }
         |
       """.stripMargin
    )

  private val verifierV4 =
    compile(
      s"""
         |{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |
         | match tx {
         |   case sstx: SetScriptTransaction =>
         |     sstx.bodyBytes.size() == $expectedBodyBytesSize                 &&
         |     sigVerify(sstx.bodyBytes, sstx.proofs[0], sstx.senderPublicKey)
         |
         |  case _ =>
         |     throw("unexpected")
         | }
         |
       """.stripMargin
    )

  private def dApp(letCount: Int) = {
    val body = (1 to letCount).map(i => s"let a$i = 1 ").mkString
    compile(
      s"""
         | {-# STDLIB_VERSION 4 #-}
         | {-# CONTENT_TYPE DAPP #-}
         | {-# SCRIPT_TYPE ACCOUNT #-}
         |
         | $body
       """.stripMargin
    )
  }

  test("big SetScript body bytes") {
    checkByteVectorLimit(firstAddress, verifierV3)
    checkByteVectorLimit(secondAddress, verifierV4)

    (the[RuntimeException] thrownBy dApp(1782)).getMessage shouldBe "Script is too large: 32780 bytes > 32768 bytes"
  }

  private def checkByteVectorLimit(address: String, verifier: String) = {
    val setScriptId = sender.setScript(address, Some(verifier), setScriptFee, waitForTx = true).id
    sender.transactionInfo[TransactionInfo](setScriptId).script.get.startsWith("base64:") shouldBe true

    val scriptInfo = sender.addressScriptInfo(address)
    scriptInfo.script.isEmpty shouldBe false
    scriptInfo.scriptText.isEmpty shouldBe false
    scriptInfo.script.get.startsWith("base64:") shouldBe true

    sender.setScript(address, Some(dApp(1781)), setScriptFee + smartFee, waitForTx = true)
  }
}
