package com.aeneas.protobuf.block

import com.aeneas.account.PublicKey
import com.aeneas.block.Block.BlockId
import com.aeneas.common.utils.EitherExt2
import com.aeneas.network.MicroBlockResponse
import com.aeneas.protobuf.transaction.PBTransactions

import scala.util.Try

object PBMicroBlocks {
  import com.aeneas.protobuf.utils.PBImplicitConversions._

  def vanilla(signedMicro: PBSignedMicroBlock, unsafe: Boolean = false): Try[MicroBlockResponse] = Try {
    require(signedMicro.microBlock.isDefined, "microblock is missing")
    val microBlock   = signedMicro.getMicroBlock
    val transactions = microBlock.transactions.map(PBTransactions.vanilla(_, unsafe).explicitGet())
    MicroBlockResponse(
      VanillaMicroBlock(
        microBlock.version.toByte,
        PublicKey(microBlock.senderPublicKey.toByteArray),
        transactions,
        microBlock.reference.toByteStr,
        microBlock.updatedBlockSignature.toByteStr,
        signedMicro.signature.toByteStr
      ),
      signedMicro.totalBlockId.toByteStr
    )
  }

  def protobuf(microBlock: VanillaMicroBlock, totalBlockId: BlockId): PBSignedMicroBlock =
    new PBSignedMicroBlock(
      microBlock = Some(
        PBMicroBlock(
          version = microBlock.version,
          reference = microBlock.reference.toByteString,
          updatedBlockSignature = microBlock.totalResBlockSig.toByteString,
          senderPublicKey = microBlock.sender.toByteString,
          transactions = microBlock.transactionData.map(PBTransactions.protobuf)
        )
      ),
      signature = microBlock.signature.toByteString,
      totalBlockId = totalBlockId.toByteString
    )
}
