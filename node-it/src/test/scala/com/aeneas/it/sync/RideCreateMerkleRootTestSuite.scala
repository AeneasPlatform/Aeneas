package com.aeneas.it.sync

import com.typesafe.config.Config
import com.aeneas.account._
import com.aeneas.common.merkle.Merkle
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.{Base58, EitherExt2}
import com.aeneas.features.BlockchainFeatures
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.api.Transaction
import com.aeneas.it.transactions.NodesFromDocker
import com.aeneas.it.{Node, NodeConfigs, ReportingTestName, TransferSending}
import com.aeneas.lang.v1.compiler.Terms._
import com.aeneas.lang.v1.estimator.v3.ScriptEstimatorV3
import com.aeneas.state._
import com.aeneas.transaction.Asset._
import com.aeneas.transaction.{Proofs, TxVersion}
import com.aeneas.transaction.smart.script.ScriptCompiler
import com.aeneas.transaction.transfer.TransferTransaction
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{CancelAfterFailure, FunSuite, Matchers}


class RideCreateMerkleRootTestSuite
    extends FunSuite
    with CancelAfterFailure
    with TransferSending
    with NodesFromDocker
    with ReportingTestName
    with Matchers
    with TableDrivenPropertyChecks {
  override def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .overrideBase(_.preactivatedFeatures((14, 1000000), BlockchainFeatures.NG.id.toInt -> 0, BlockchainFeatures.FairPoS.id.toInt -> 0, BlockchainFeatures.Ride4DApps.id.toInt -> 0, BlockchainFeatures.BlockV5.id.toInt -> 0))
      .withDefault(1)
      .buildNonConflicting()

  private def sender: Node         = nodes.last

  test("Ride createMerkleRoot") {
    val script =  """
        |{-# STDLIB_VERSION 4 #-}
        |{-# CONTENT_TYPE DAPP #-}
        |
        | @Callable(inv)
        |func foo(proof: List[ByteVector], id: ByteVector, index: Int) = [
        | BinaryEntry("root", createMerkleRoot(proof, id, index))
        |]
        """.stripMargin
    val cscript = ScriptCompiler.compile(script, ScriptEstimatorV3).explicitGet()._1.bytes().base64
    val node = nodes.head
    nodes.waitForHeightArise()
    val tx1 = node.broadcastTransfer(node.keyPair, sender.address, setScriptFee, minFee, None, None, version = TxVersion.V3, waitForTx = false)
    val txId1 = tx1.id
    val tx2 = node.broadcastTransfer(node.keyPair, node.address, 1, minFee, None, None, version = TxVersion.V3, waitForTx = false)
    val txId2 = tx2.id
    val tx3 = node.broadcastTransfer(node.keyPair, node.address, 1, minFee, None, None, version = TxVersion.V3, waitForTx = false)
    val txId3 = tx3.id
    val tx4 = node.broadcastTransfer(node.keyPair, node.address, 1, minFee, None, None, version = TxVersion.V3, waitForTx = false)
    val txId4 = tx4.id
    val tx5 = node.broadcastTransfer(node.keyPair, node.address, 1, minFee, None, None, version = TxVersion.V3, waitForTx = false)
    val txId5 = tx5.id

    val height = node.height

    nodes.waitForHeightArise()

    def tt(tx: Transaction) = TransferTransaction.create(
      tx.version.get,
      PublicKey(Base58.decode(tx.senderPublicKey.get)),
      Address.fromString(tx.recipient.get).explicitGet(),
      Waves /* not support tx.asset.fold(Waves)(v => IssuedAsset(Base58.decode(v))) */,
      tx.amount.get,
      Waves /* not support tx.feeAsset.fold(Waves)(v => Issued(Base58.decode(v))) */,
      tx.fee, ByteStr.empty,  // attachment
      tx.timestamp,
      Proofs(tx.proofs.get.map(v => ByteStr(Base58.decode(v))))
      ).explicitGet()
    val natives = Seq(tx1, tx2, tx3, tx4, tx5).map(tt).map(t => Base58.encode(t.id().arr) -> t).toMap

    val root = Base58.decode(node.blockAt(height).transactionsRoot.get)

    val proofs = nodes.head.getMerkleProof(txId1, txId2, txId3, txId4, txId5)

    sender.setScript(sender.address, Some(cscript), setScriptFee, waitForTx = true).id

    for(p <- proofs) {
      node.invokeScript(
        node.address,
        sender.address,
        func = Some("foo"),
        args = List(ARR(p.merkleProof.map(v => CONST_BYTESTR(ByteStr(Base58.decode(v))).explicitGet()).toIndexedSeq, false).explicitGet(),
                    CONST_BYTESTR(ByteStr(Merkle.hash(natives(p.id).bytes()))).explicitGet(),
                    CONST_LONG(p.transactionIndex.toLong)),
        payment = Seq(),
        fee = 2*smartFee+minFee,
        waitForTx = true
      )
      node.getDataByKey(sender.address, "root") shouldBe BinaryDataEntry("root", ByteStr(root))
    }
  }
}
