package com.aeneas.it

import com.aeneas.account.KeyPair
import com.aeneas.common.utils.{Base58, EitherExt2}
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.util._
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.smart.SetScriptTransaction
import com.aeneas.transaction.smart.script.ScriptCompiler
import com.aeneas.transaction.transfer._
import com.aeneas.utils.ScorexLogging
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

trait IntegrationSuiteWithThreeAddresses
    extends BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with RecoverMethods
    with IntegrationTestsScheme
    with Nodes
    with ScorexLogging {
  this: Suite =>

  def miner: Node    = nodes.head
  def notMiner: Node = nodes.last

  protected def sender: Node = miner

  protected lazy val firstAddress: String  = sender.createAddress()
  protected lazy val secondAddress: String = sender.createAddress()
  protected lazy val thirdAddress: String  = sender.createAddress()

  def pkByAddress(address: String): KeyPair =
    KeyPair(Base58.decode(sender.seed(address)))

  abstract protected override def beforeAll(): Unit = {
    super.beforeAll()

    val defaultBalance: Long = 100.waves

    def dumpBalances(node: Node, accounts: Seq[String], label: String): Unit = {
      accounts.foreach(acc => {
        val (balance, eff) = miner.accountBalances(acc)

        val formatted = s"$acc: balance = $balance, effective = $eff"
        log.debug(s"$label account balance:\n$formatted")
      })
    }

    def waitForTxsToReachAllNodes(txIds: Seq[String]) = {
      val txNodePairs = for {
        txId <- txIds
        node <- nodes
      } yield (node, txId)

      txNodePairs.foreach({ case (node, tx) => node.waitForTransaction(tx) })
    }

    def makeTransfers(accounts: Seq[String]): Seq[String] = accounts.map { acc =>
      sender.transfer(sender.address, acc, defaultBalance, sender.fee(TransferTransaction.typeId)).id

    }

    def correctStartBalancesFuture(): Unit = {
      nodes.waitForHeight(2)
      val accounts = Seq(firstAddress, secondAddress, thirdAddress)

      dumpBalances(sender, accounts, "initial")
      val txs = makeTransfers(accounts)

      val height = nodes.map(_.height).max

      withClue(s"waitForHeight(${height + 2})") {
        nodes.waitForHeight(height + 2)
      }

      withClue("waitForTxsToReachAllNodes") {
        waitForTxsToReachAllNodes(txs)
      }

      dumpBalances(sender, accounts, "after transfer")
      accounts.foreach(miner.assertBalances(_, defaultBalance, defaultBalance))
    }

    withClue("beforeAll") {
      correctStartBalancesFuture()
    }
  }

  def setContract(contractText: Option[String], acc: KeyPair): String = {
    val script = contractText.map { x =>
      val scriptText = x.stripMargin
      ScriptCompiler(scriptText, isAssetScript = false, ScriptEstimatorV2).explicitGet()._1
    }
    val setScriptTransaction = SetScriptTransaction
      .selfSigned(1.toByte, acc, script, 0.014.waves, System.currentTimeMillis())
      .explicitGet()
    sender
      .signedBroadcast(setScriptTransaction.json(), waitForTx = true)
      .id
  }

  def setContracts(contracts: (Option[String], KeyPair)*): Unit = {
    contracts
      .map {
        case (src, acc) => setContract(src, acc)
      }
      .foreach(id => sender.waitForTransaction(id))
    nodes.waitForHeightArise()
  }
}
