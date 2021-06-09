package com.aeneas.state

import com.aeneas.block.Block.BlockId
import com.aeneas.block.MicroBlock
import com.aeneas.common.state.ByteStr

trait NG {
  def microBlock(id: ByteStr): Option[MicroBlock]

  def bestLastBlockInfo(maxTimestamp: Long): Option[BlockMinerInfo]

  def microblockIds: Seq[BlockId]
}
