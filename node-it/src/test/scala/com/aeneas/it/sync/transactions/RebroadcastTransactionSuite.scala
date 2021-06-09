package com.aeneas.it.sync.transactions

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory.parseString
import com.aeneas.account.Address
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.Node
import com.aeneas.it.NodeConfigs._
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync._
import com.aeneas.it.transactions.{BaseTransactionSuite, NodesFromDocker}
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.transfer.TransferTransaction

class RebroadcastTransactionSuite extends BaseTransactionSuite with NodesFromDocker {

  import RebroadcastTransactionSuite._

  override protected def nodeConfigs: Seq[Config] =
    Seq(configWithRebroadcastAllowed.withFallback(NotMiner), configWithRebroadcastAllowed.withFallback(Miners.head))

  private def nodeA: Node = nodes.head
  private def nodeB: Node = nodes.last

  test("should rebroadcast a transaction if that's allowed in config") {
    val tx = TransferTransaction.selfSigned(2.toByte, nodeA.keyPair, Address.fromString(nodeB.address).explicitGet(), Waves, transferAmount, Waves, minFee, ByteStr.empty,  System.currentTimeMillis())
      .explicitGet()
      .json()

    val dockerNodeBId = docker.stopContainer(dockerNodes.apply().last)
    val txId          = nodeA.signedBroadcast(tx).id
    docker.startContainer(dockerNodeBId)
    nodeA.waitForPeers(1)

    nodeB.ensureTxDoesntExist(txId)
    nodeA.signedBroadcast(tx)
    nodeB.waitForUtxIncreased(0)
    nodeB.utxSize shouldBe 1
  }

  test("should not rebroadcast a transaction if that's not allowed in config") {
    dockerNodes().foreach(docker.restartNode(_, configWithRebroadcastNotAllowed))
    val tx = TransferTransaction
      .selfSigned(2.toByte, nodeA.keyPair, Address.fromString(nodeB.address).explicitGet(), Waves, transferAmount, Waves, minFee, ByteStr.empty,  System.currentTimeMillis())
      .explicitGet()
      .json()

    val dockerNodeBId = docker.stopContainer(dockerNodes.apply().last)
    val txId          = nodeA.signedBroadcast(tx).id
    docker.startContainer(dockerNodeBId)
    nodeA.waitForPeers(1)

    nodeB.ensureTxDoesntExist(txId)
    nodeA.signedBroadcast(tx)
    nodes.waitForHeightArise()
    nodeB.utxSize shouldBe 0
    nodeB.ensureTxDoesntExist(txId)

  }
}
object RebroadcastTransactionSuite {

  private val configWithRebroadcastAllowed =
    parseString("waves.synchronization.utx-synchronizer.allow-tx-rebroadcasting = true")

  private val configWithRebroadcastNotAllowed =
    parseString("waves.synchronization.utx-synchronizer.allow-tx-rebroadcasting = false")

}
