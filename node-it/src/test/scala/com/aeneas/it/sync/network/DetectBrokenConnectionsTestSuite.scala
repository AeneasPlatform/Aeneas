package com.aeneas.it.sync.network

import com.typesafe.config.{Config, ConfigFactory}
import com.aeneas.it.NodeConfigs.Default
import com.aeneas.it.ReportingTestName
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.transactions.NodesFromDocker
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._

class DetectBrokenConnectionsTestSuite extends FreeSpec with Matchers with ReportingTestName with NodesFromDocker {

  override protected def nodeConfigs: Seq[Config] = {
    val highPriorityConfig = ConfigFactory.parseString("waves.network.break-idle-connections-timeout = 20s")
    Default.take(2).map(highPriorityConfig.withFallback)
  }

  "disconnect nodes from the network and wait a timeout for detecting of broken connections" in {
    dockerNodes().foreach(docker.disconnectFromNetwork)
    Thread.sleep(30.seconds.toMillis)

    dockerNodes().foreach { node =>
      docker.connectToNetwork(Seq(node))
      node.connectedPeers shouldBe empty
      docker.disconnectFromNetwork(node)
    }

    // To prevent errors in the log
    docker.connectToNetwork(dockerNodes())
  }

}
