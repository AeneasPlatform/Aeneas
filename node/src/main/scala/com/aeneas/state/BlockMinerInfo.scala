package com.aeneas.state

import com.aeneas.block.Block.BlockId
import com.aeneas.common.state.ByteStr

case class BlockMinerInfo(baseTarget: Long, generationSignature: ByteStr, timestamp: Long, blockId: BlockId)
