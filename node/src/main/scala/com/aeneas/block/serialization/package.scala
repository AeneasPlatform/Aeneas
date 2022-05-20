package com.aeneas.block

import java.nio.ByteBuffer

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.aeneas.block.Block.{GenesisBlockVersion, NgBlockVersion, PlainBlockVersion, ProtoBlockVersion, RewardBlockVersion}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils._
import com.aeneas.protobuf.transaction.PBTransactions
import com.wavesplatform.protobuf.transaction.SignedTransaction
import com.aeneas.protobuf.utils.PBUtils
import com.aeneas.serialization.ByteBufferOps
import com.aeneas.transaction.{Transaction, TransactionParsers}

package object serialization {
  private[block] def writeTransactionData(version: Byte, txs: Seq[Transaction]): Array[Byte] = {
    val txsBytes     = txs.map(tx => if (version == ProtoBlockVersion) PBUtils.encodeDeterministic(PBTransactions.protobuf(tx)) else tx.bytes())
    val txsBytesSize = txsBytes.map(_.length + Ints.BYTES).sum
    val txsBuf       = ByteBuffer.allocate(txsBytesSize)
    txsBytes.foreach(tx => txsBuf.putInt(tx.length).put(tx))

    Bytes.concat(mkTxsCountBytes(version, txs.size), txsBuf.array())
  }

  private[block] def readTransactionData(version: Byte, buf: ByteBuffer): Seq[Transaction] = {
    val txCount = version match {
      case GenesisBlockVersion | PlainBlockVersion                 => buf.getByte
      case NgBlockVersion | RewardBlockVersion | ProtoBlockVersion => buf.getInt
    }

    val txs = (1 to txCount).foldLeft(List.empty[Transaction]) {
      case (txs, _) =>
        val size    = buf.getInt
        val txBytes = buf.getByteArray(size)
        val tx = version match {
          case ProtoBlockVersion => PBTransactions.vanilla(SignedTransaction.parseFrom(txBytes)).explicitGet()
          case _                 => TransactionParsers.parseBytes(txBytes).get
        }
        tx :: txs
    }
    txs.reverse
  }

  private[block] def writeConsensusBytes(baseTarget: Long, generationSignature: ByteStr): Array[Byte] =
    Bytes.concat(
      Longs.toByteArray(baseTarget),
      generationSignature.arr
    )

  def mkTxsCountBytes(version: Byte, txsCount: Int): Array[Byte] = version match {
    case GenesisBlockVersion | PlainBlockVersion                 => Array(txsCount.toByte)
    case NgBlockVersion | RewardBlockVersion | ProtoBlockVersion => Ints.toByteArray(txsCount)
  }
}
