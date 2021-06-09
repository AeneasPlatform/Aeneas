package com.aeneas.mining.microblocks

import com.aeneas.account.KeyPair
import com.aeneas.block.Block
import com.aeneas.mining.{MinerDebugInfo, MiningConstraint}
import com.aeneas.settings.MinerSettings
import com.aeneas.state.Blockchain
import com.aeneas.transaction.BlockchainUpdater
import com.aeneas.utx.UtxPool
import io.netty.channel.group.ChannelGroup
import monix.eval.Task
import monix.execution.schedulers.SchedulerService

trait MicroBlockMiner {
  def generateMicroBlockSequence(
      account: KeyPair,
      accumulatedBlock: Block,
      restTotalConstraint: MiningConstraint,
      lastMicroBlock: Long
  ): Task[Unit]
}

object MicroBlockMiner {
  def apply(
      setDebugState: MinerDebugInfo.State => Unit,
      allChannels: ChannelGroup,
      blockchainUpdater: BlockchainUpdater with Blockchain,
      utx: UtxPool,
      settings: MinerSettings,
      minerScheduler: SchedulerService,
      appenderScheduler: SchedulerService
  ): MicroBlockMiner =
    new MicroBlockMinerImpl(
      setDebugState,
      allChannels,
      blockchainUpdater,
      utx,
      settings,
      minerScheduler,
      appenderScheduler
    )
}
