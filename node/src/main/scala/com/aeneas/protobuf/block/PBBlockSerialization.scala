package com.aeneas.protobuf.block
import com.aeneas.protobuf.utils.PBUtils
import com.google.protobuf.ByteString

private[block] object PBBlockSerialization {
  def signedBytes(block: PBBlock): Array[Byte] = {
    PBUtils.encodeDeterministic(block)
  }

  def unsignedBytes(block: PBBlock): Array[Byte] = {
    PBUtils.encodeDeterministic(block.withSignature(ByteString.EMPTY))
  }
}
