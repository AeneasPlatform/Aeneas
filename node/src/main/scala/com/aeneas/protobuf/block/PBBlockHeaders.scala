package com.aeneas.protobuf.block

import com.google.protobuf.ByteString
import com.aeneas.account.{AddressScheme, PublicKey}
import com.aeneas.block.BlockHeader
import com.aeneas.common.state.ByteStr

object PBBlockHeaders {
  def protobuf(header: VanillaBlockHeader): PBBlockHeader = {
    import header._

    PBBlock.Header(
      AddressScheme.current.chainId,
      ByteString.copyFrom(reference.arr),
      baseTarget,
      ByteString.copyFrom(generationSignature.arr),
      header.featureVotes.map(shortToInt),
      header.timestamp,
      header.version,
      ByteString.copyFrom(generator.arr),
      header.rewardVote,
      ByteString.copyFrom(header.transactionsRoot.arr)
    )
  }

  def vanilla(header: PBBlockHeader): VanillaBlockHeader =
    BlockHeader(
      header.version.toByte,
      header.timestamp,
      ByteStr(header.reference.toByteArray),
      header.baseTarget,
      ByteStr(header.generationSignature.toByteArray),
      PublicKey(header.generator.toByteArray),
      header.featureVotes.map(intToShort),
      header.rewardVote,
      ByteStr(header.transactionsRoot.toByteArray)
    )

  private[this] def shortToInt(s: Short): Int = {
    java.lang.Short.toUnsignedInt(s)
  }

  private[this] def intToShort(int: Int): Short = {
    require(int >= 0 && int <= 65535, s"Short overflow: $int")
    int.toShort
  }
}
