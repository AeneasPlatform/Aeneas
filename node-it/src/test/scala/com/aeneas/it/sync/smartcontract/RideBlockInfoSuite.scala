package com.aeneas.it.sync.smartcontract

import com.typesafe.config.Config
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.NodeConfigs
import com.aeneas.it.NodeConfigs.Default
import com.aeneas.it.api.Block
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.lang.v1.compiler.Terms.CONST_LONG
import com.aeneas.lang.v1.estimator.v3.ScriptEstimatorV3
import com.aeneas.transaction.smart.script.ScriptCompiler

class RideBlockInfoSuite extends BaseTransactionSuite {
  val activationHeight = 4

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs
      .Builder(Default, 1, Seq.empty)
      .overrideBase(_.quorum(0))
      .overrideBase(_.preactivatedFeatures((14, 2)))
      .overrideBase(_.preactivatedFeatures((15, activationHeight)))
      .buildNonConflicting()

  private val dAppScriptV4 =
    """
      |{-# STDLIB_VERSION 4 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |
      |@Callable(i)
      |func blockInfo(h: Int) = {
      |    let block = match blockInfoByHeight(h) {
      |        case block: BlockInfo => block
      |        case _ => throw("can't find block")
      |    }
      |    [
      |        IntegerEntry("timestamp", block.timestamp),
      |        IntegerEntry("height", block.height),
      |        IntegerEntry("baseTarget", block.baseTarget),
      |        BinaryEntry("generationSignature", block.generationSignature),
      |        BinaryEntry("generator", block.generator.bytes),
      |        BinaryEntry("generatorPublicKey", block.generatorPublicKey)
      |    ]
      |}
      |
      |@Callable(i)
      |func blockInfoV5(h: Int) = {
      |    let block = match blockInfoByHeight(h) {
      |        case block: BlockInfo => block
      |        case _ => throw("can't find block")
      |    }
      |    [
      |        IntegerEntry("timestamp", block.timestamp),
      |        IntegerEntry("height", block.height),
      |        IntegerEntry("baseTarget", block.baseTarget),
      |        BinaryEntry("generationSignature", block.generationSignature),
      |        BinaryEntry("generator", block.generator.bytes),
      |        BinaryEntry("generatorPublicKey", block.generatorPublicKey),
      |        BinaryEntry("vrf", block.vrf.value())
      |    ]
      |}
      |""".stripMargin

  private val caller = firstAddress
  private val dApp   = secondAddress

  protected override def beforeAll(): Unit ={
    super.beforeAll()
    val script = ScriptCompiler.compile(dAppScriptV4, ScriptEstimatorV3).explicitGet()._1.bytes().base64
    sender.setScript(dApp, Some(script), waitForTx = true)
  }

  test("able to retrieve block V5 info with vrf") {
    val height = activationHeight
    sender.invokeScript(caller, dApp, func = Some("blockInfoV5"), args = List(CONST_LONG(height)), waitForTx = true)

    val block = sender.blockAt(height)
    checkCommonFields(block)
    sender.getDataByKey(dApp, "vrf").value.asInstanceOf[ByteStr] shouldBe ByteStr.decodeBase58(block.vrf.get).get
  }

  test("not able to retrieve vrf from block V4") {
    val tx = sender.invokeScript(caller, dApp, func = Some("blockInfoV5"), args = List(CONST_LONG(activationHeight - 1)))._1.id
    sender.waitForHeight(sender.height + 1)
    sender.transactionStatus(Seq(tx)).head.status shouldBe "not_found"
  }

  test("not able to retrieve vrf from block V3") {
    val tx = sender.invokeScript(caller, dApp, func = Some("blockInfoV5"), args = List(CONST_LONG(activationHeight - 2)))._1.id
    sender.waitForHeight(sender.height + 1)
    sender.transactionStatus(Seq(tx)).head.status shouldBe "not_found"
  }

  test("able to retrieve block V4 info") {
    val height = activationHeight - 1
    sender.invokeScript(caller, dApp, func = Some("blockInfo"), args = List(CONST_LONG(height)), waitForTx = true)

    checkCommonFields(sender.blockAt(height))
  }

  test("able to retrieve block V3 info") {
    val height = activationHeight - 2
    sender.invokeScript(caller, dApp, func = Some("blockInfo"), args = List(CONST_LONG(height)), waitForTx = true)

    checkCommonFields(sender.blockAt(height))
  }

  test("able to retrieve genesis block info") {
    val height = 1
    sender.invokeScript(caller, dApp, func = Some("blockInfo"), args = List(CONST_LONG(height)), waitForTx = true)

    checkCommonFields(sender.blockAt(height))
  }

  test("liquid blocks don't affect vrf field") {
    val height = miner.height + 1
    nodes.waitForHeight(height)

    sender.invokeScript(caller, dApp, func = Some("blockInfoV5"), args = List(CONST_LONG(height)), waitForTx = true)
    val vrf1 = sender.getDataByKey(dApp, "vrf").value.asInstanceOf[ByteStr]
    vrf1 shouldBe ByteStr.decodeBase58(sender.blockAt(height).vrf.get).get

    sender.transfer(caller, dApp, 1, waitForTx = true)

    sender.invokeScript(caller, dApp, func = Some("blockInfoV5"), args = List(CONST_LONG(height)), waitForTx = true)
    val vrf2 = sender.getDataByKey(dApp, "vrf").value.asInstanceOf[ByteStr]
    vrf2 shouldBe ByteStr.decodeBase58(sender.blockAt(height).vrf.get).get

    vrf1 shouldBe vrf2
  }

  private def checkCommonFields(block: Block) = {
    sender.getDataByKey(dApp, "timestamp").value.asInstanceOf[Long] shouldBe block.timestamp
    sender.getDataByKey(dApp, "height").value.asInstanceOf[Long] shouldBe block.height
    sender.getDataByKey(dApp, "baseTarget").value.asInstanceOf[Long] shouldBe block.baseTarget.get
    sender.getDataByKey(dApp, "generationSignature").value.asInstanceOf[ByteStr].toString shouldBe block.generationSignature.get
    sender.getDataByKey(dApp, "generator").value.asInstanceOf[ByteStr] shouldBe ByteStr.decodeBase58(block.generator).get
    sender.getDataByKey(dApp, "generatorPublicKey").value.asInstanceOf[ByteStr].arr shouldBe block.generatorPublicKey.arr
  }
}
