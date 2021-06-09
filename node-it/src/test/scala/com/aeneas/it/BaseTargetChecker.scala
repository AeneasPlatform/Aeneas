package com.aeneas.it

import com.typesafe.config.ConfigFactory.{defaultApplication, defaultReference}
import com.aeneas.account.KeyPair
import com.aeneas.block.Block
import com.aeneas.common.utils.EitherExt2
import com.aeneas.consensus.PoSSelector
import com.aeneas.database.openDB
import com.aeneas.events.BlockchainUpdateTriggers
import com.aeneas.history.StorageFactory
import com.aeneas.settings._
import com.aeneas.transaction.Asset.Waves
import com.aeneas.utils.NTP
import monix.execution.UncaughtExceptionReporter
import monix.reactive.Observer
import net.ceedubs.ficus.Ficus._

object BaseTargetChecker {
  def main(args: Array[String]): Unit = {
    implicit val reporter: UncaughtExceptionReporter = UncaughtExceptionReporter.default
    val sharedConfig = Docker.genesisOverride
      .withFallback(Docker.configTemplate)
      .withFallback(defaultApplication())
      .withFallback(defaultReference())
      .resolve()

    val settings          = WavesSettings.fromRootConfig(sharedConfig)
    val db                = openDB("/tmp/tmp-db")
    val ntpTime           = new NTP("ntp.pool.org")
    val (blockchainUpdater, _) = StorageFactory(settings, db, ntpTime, Observer.empty, BlockchainUpdateTriggers.noop)
    val poSSelector       = PoSSelector(blockchainUpdater, settings.synchronizationSettings)

    try {
      val genesisBlock = Block.genesis(settings.blockchainSettings.genesisSettings).explicitGet()
      blockchainUpdater.processBlock(genesisBlock, genesisBlock.header.generationSignature)

      NodeConfigs.Default.map(_.withFallback(sharedConfig)).collect {
        case cfg if cfg.as[Boolean]("waves.miner.enable") =>
          val account = KeyPair.fromSeed(cfg.getString("account-seed")).explicitGet()
          val address   = account.toAddress
          val balance   = blockchainUpdater.balance(address, Waves)
          val timeDelay = poSSelector
            .getValidBlockDelay(blockchainUpdater.height, account, genesisBlock.header.baseTarget, balance)
            .explicitGet()

          f"$address: ${timeDelay * 1e-3}%10.3f s"
      }
    } finally ntpTime.close()
  }
}
