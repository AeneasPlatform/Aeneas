package com.aeneas.it.async

import com.typesafe.config.{Config, ConfigFactory}
import com.aeneas.it.NodeConfigs.Default
import com.aeneas.it.api.AsyncHttpApi._
import com.aeneas.it.transactions.NodesFromDocker
import com.aeneas.it.util._
import com.aeneas.utils.ScorexLogging
import org.scalatest.{CancelAfterFailure, FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.traverse
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class MicroblocksFeeTestSuite extends FreeSpec with Matchers with CancelAfterFailure with NodesFromDocker with ScorexLogging {

  private def notMiner = nodes.head

  private def firstAddress = nodes(1).address

  private def txRequestsGen(n: Int, fee: Long): Future[Unit] = {
    val parallelRequests = 10

    def requests(n: Int): Future[Unit] =
      Future
        .sequence {
          //Not mining node sends transfer transactions to another not mining node
          //Mining nodes collect fee
          (1 to n).map { _ =>
            notMiner.transfer(notMiner.address, firstAddress, (1 + Random.nextInt(10)).waves, fee)
          }
        }
        .map(_ => ())

    val steps = (1 to n)
      .sliding(parallelRequests, parallelRequests)
      .map(_.size)

    steps.foldLeft(Future.successful(())) { (r, numRequests) =>
      r.flatMap(_ => requests(numRequests))
    }
  }

  "fee distribution when NG activates" in {
    val f = for {
      _ <- traverse(nodes)(_.height).map(_.max)

      _ <- traverse(nodes)(_.waitForHeight(microblockActivationHeight - 1))
      _ <- txRequestsGen(200, 2.waves)
      _ <- traverse(nodes)(_.waitForHeight(microblockActivationHeight + 3))

      initialBalances <- notMiner.debugStateAt(microblockActivationHeight - 1) //100%

      balancesBeforeActivation <- notMiner.debugStateAt(microblockActivationHeight) // 100%
      blockBeforeActivation    <- notMiner.blockHeadersAt(microblockActivationHeight)

      balancesOnActivation <- notMiner.debugStateAt(microblockActivationHeight + 1) // 40%
      blockOnActivation    <- notMiner.blockHeadersAt(microblockActivationHeight + 1)

      balancesAfterActivation <- notMiner.debugStateAt(microblockActivationHeight + 2) // 60% of previous + 40% of current
      blockAfterActivation    <- notMiner.blockHeadersAt(microblockActivationHeight + 2)
    } yield {

      balancesBeforeActivation(blockBeforeActivation.generator) shouldBe {
        nodes.head.settings.blockchainSettings.rewardsSettings.initial +
          initialBalances(blockBeforeActivation.generator) + blockBeforeActivation.totalFee
      }

      balancesOnActivation(blockOnActivation.generator) shouldBe {
        nodes.head.settings.blockchainSettings.rewardsSettings.initial +
          balancesBeforeActivation(blockOnActivation.generator) + blockOnActivation.totalFee * 4 / 10
      }

      balancesAfterActivation(blockAfterActivation.generator) shouldBe {
        nodes.head.settings.blockchainSettings.rewardsSettings.initial +
          balancesOnActivation(blockAfterActivation.generator) + blockOnActivation.totalFee * 6 / 10 +
          blockAfterActivation.totalFee * 4 / 10
      }
    }

    Await.result(f, 5.minute)
  }

  private val microblockActivationHeight = 10
  private val minerConfig = ConfigFactory.parseString(
    s"""waves {
       |  blockchain.custom.functionality.pre-activated-features.2 = $microblockActivationHeight
       |  miner.quorum = 3
       |}
      """.stripMargin
  )

  private val notMinerConfig = ConfigFactory.parseString(
    s"""waves {
       |  blockchain.custom.functionality.pre-activated-features.2 = $microblockActivationHeight
       |  miner.enable = no
       |}
      """.stripMargin
  )

  override protected def nodeConfigs: Seq[Config] = Seq(
    notMinerConfig.withFallback(Default.head),
    notMinerConfig.withFallback(Default(1)),
    notMinerConfig.withFallback(Default(2)),
    minerConfig.withFallback(Default(3))
  )
}
