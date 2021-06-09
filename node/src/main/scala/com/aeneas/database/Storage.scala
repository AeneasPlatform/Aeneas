package com.aeneas.database

import com.aeneas.block.Block
import com.aeneas.common.state.ByteStr
import com.aeneas.state.Diff

trait Storage {
  def append(diff: Diff, carryFee: Long, totalFee: Long, reward: Option[Long], hitSource: ByteStr, block: Block): Unit
  def lastBlock: Option[Block]
  def rollbackTo(targetBlockId: ByteStr): Either[String, Seq[(Block, ByteStr)]]
}
