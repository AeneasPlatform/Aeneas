package com.aeneas.block.serialization

import java.nio.ByteBuffer

import com.google.common.primitives.{Bytes, Ints}
import com.aeneas.block.{Block, MicroBlock}
import com.aeneas.common.state.ByteStr
import com.aeneas.crypto.SignatureLength
import com.aeneas.serialization.ByteBufferOps

import scala.util.Try

object MicroBlockSerializer {
  def toBytes(microBlock: MicroBlock): Array[Byte] = {
    val transactionDataBytes = writeTransactionData(microBlock.version, microBlock.transactionData)
    Bytes.concat(
      Array(microBlock.version),
      microBlock.reference.arr,
      microBlock.totalResBlockSig.arr,
      Ints.toByteArray(transactionDataBytes.length),
      transactionDataBytes,
      microBlock.sender.arr,
      microBlock.signature.arr
    )
  }

  def parseBytes(bytes: Array[Byte]): Try[MicroBlock] =
    Try {
      val buf = ByteBuffer.wrap(bytes).asReadOnlyBuffer()

      val version          = buf.get
      val reference        = ByteStr(buf.getByteArray(Block.referenceLength(version)))
      val totalResBlockSig = ByteStr(buf.getByteArray(SignatureLength))

      buf.getInt

      val transactionData = readTransactionData(version, buf)
      val generator       = buf.getPublicKey
      val signature       = ByteStr(buf.getByteArray(SignatureLength))

      MicroBlock(version, generator, transactionData, reference, totalResBlockSig, signature)
    }
}
