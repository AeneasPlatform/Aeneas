package com.aeneas.api.grpc

import com.google.protobuf.ByteString
import com.aeneas.account.{Address, AddressScheme, PublicKey}
import com.aeneas.block.BlockHeader
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils._
import com.aeneas.lang.ValidationError
import com.aeneas.protobuf.block.{PBBlock, PBBlocks, VanillaBlock}
import com.aeneas.protobuf.transaction.{PBRecipients, PBSignedTransaction, PBTransactions, VanillaTransaction}
//import com.aeneas.protobuf.block.{PBBlock, PBBlocks, VanillaBlock}
import com.wavesplatform.protobuf.transaction._
import com.aeneas.{block => vb}

//noinspection ScalaStyle
trait PBImplicitConversions {
  implicit class VanillaTransactionConversions(tx: VanillaTransaction) {
    def toPB: PBSignedTransaction = PBTransactions.protobuf(tx)
  }

  implicit class PBSignedTransactionConversions(tx: PBSignedTransaction) {
    def toVanilla: Either[ValidationError, VanillaTransaction] = PBTransactions.vanilla(tx)
  }

  implicit class VanillaBlockConversions(block: VanillaBlock) {
    def toPB: PBBlock = PBBlocks.protobuf(block)
  }

  implicit class PBBlockHeaderConversionOps(header: PBBlock.Header) {
    def toVanilla(signature: ByteStr): vb.BlockHeader = {
      BlockHeader(
        header.version.toByte,
        header.timestamp,
        header.reference.toByteStr,
        header.baseTarget,
        header.generationSignature.toByteStr,
        header.generator.toPublicKey,
        header.featureVotes.map(intToShort),
        header.rewardVote,
        header.transactionsRoot.toByteStr
      )
    }
  }

  implicit class VanillaHeaderConversionOps(header: vb.BlockHeader) {
    def toPBHeader: PBBlock.Header = PBBlock.Header(
      0: Byte,
      header.reference.toPBByteString,
      header.baseTarget,
      header.generationSignature.toPBByteString,
      header.featureVotes.map(shortToInt),
      header.timestamp,
      header.version,
      header.generator,
      header.rewardVote,
      ByteString.copyFrom(header.transactionsRoot.arr)
    )
  }

  implicit class PBRecipientConversions(r: Recipient) {
    def toAddress        = PBRecipients.toAddress(r, AddressScheme.current.chainId).explicitGet()
    def toAddressOrAlias = PBRecipients.toAddressOrAlias(r, AddressScheme.current.chainId).explicitGet()
  }

  implicit class VanillaByteStrConversions(bytes: ByteStr) {
    def toPBByteString = ByteString.copyFrom(bytes.arr)
  }

  implicit class PBByteStringConversions(bytes: ByteString) {
    def toByteStr   = ByteStr(bytes.toByteArray)
    def toPublicKey = PublicKey(bytes.toByteArray)
    def toAddress: Address =
      PBRecipients.toAddress(bytes.toByteArray, AddressScheme.current.chainId).fold(ve => throw new IllegalArgumentException(ve.toString), identity)
  }

  implicit def vanillaByteStrToPBByteString(bs: ByteStr): ByteString = bs.toPBByteString
  implicit def pbByteStringToVanillaByteStr(bs: ByteString): ByteStr = bs.toByteStr

  private[this] implicit def shortToInt(s: Short): Int = {
    java.lang.Short.toUnsignedInt(s)
  }

  private[this] def intToShort(int: Int): Short = {
    require(int.isValidShort, s"Short overflow: $int")
    int.toShort
  }
}
