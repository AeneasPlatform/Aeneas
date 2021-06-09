package com.aeneas.history

import com.aeneas.block.Block.BlockId
import com.aeneas.block.MicroBlock

class MicroBlockWithTotalId(val microBlock: MicroBlock, val totalBlockId: BlockId)
object MicroBlockWithTotalId {
  implicit def toMicroBlock(mb: MicroBlockWithTotalId): MicroBlock = mb.microBlock
}
