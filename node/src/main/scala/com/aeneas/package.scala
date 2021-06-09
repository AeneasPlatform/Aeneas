package com

import com.aeneas.block.Block
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.settings.WavesSettings
import com.aeneas.state.Blockchain
import com.aeneas.transaction.BlockchainUpdater
import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.utils.ScorexLogging

package object aeneas extends ScorexLogging {
  private def checkOrAppend(block: Block, blockchainUpdater: Blockchain with BlockchainUpdater): Either[ValidationError, Unit] = {
    if (blockchainUpdater.isEmpty) {
      blockchainUpdater.processBlock(block, block.header.generationSignature).map { _ =>
        log.info(s"Genesis block ${blockchainUpdater.blockHeader(1).get.header} has been added to the state")
      }
    } else {
      val existingGenesisBlockId: Option[ByteStr] = blockchainUpdater.blockHeader(1).map(_.id())
      Either.cond(existingGenesisBlockId.fold(false)(_ == block.id()),
                  (),
                  GenericError("Mismatched genesis blocks in configuration and blockchain"))
    }
  }

  def checkGenesis(settings: WavesSettings, blockchainUpdater: Blockchain with BlockchainUpdater): Unit = {
    log.debug("start genesis block gen")
    Block
      .genesis(settings.blockchainSettings.genesisSettings)
      .flatMap { genesis =>
        log.info(s"Genesis block json: ${genesis.json()}")
        checkOrAppend(genesis, blockchainUpdater)
      }
      .left
      .foreach { e =>
        log.error("INCORRECT NODE CONFIGURATION!!! NODE STOPPED BECAUSE OF THE FOLLOWING ERROR:")
        log.error(e.toString)
        com.aeneas.utils.forceStopApplication()
      }
  }
}
