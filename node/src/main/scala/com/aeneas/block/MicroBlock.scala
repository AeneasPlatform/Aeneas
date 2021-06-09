package com.aeneas.block

import com.aeneas.account.{KeyPair, PublicKey}
import com.aeneas.block.Block.BlockId
import com.aeneas.block.serialization.MicroBlockSerializer
import com.aeneas.common.state.ByteStr
import com.aeneas.crypto
import com.aeneas.lang.ValidationError
import com.aeneas.state._
import com.aeneas.transaction._
import com.aeneas.utils.ScorexLogging
import monix.eval.Coeval

import scala.util.{Failure, Try}

case class MicroBlock(
    version: Byte,
    sender: PublicKey,
    transactionData: Seq[Transaction],
    reference: BlockId,
    totalResBlockSig: BlockId,
    signature: ByteStr
) extends Signed {
  val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(MicroBlockSerializer.toBytes(this))

  private[block] val bytesWithoutSignature: Coeval[Array[Byte]] = Coeval.evalOnce(copy(signature = ByteStr.empty).bytes())

  override val signatureValid: Coeval[Boolean]        = Coeval.evalOnce(crypto.verify(signature, bytesWithoutSignature(), sender))
  override val signedDescendants: Coeval[Seq[Signed]] = Coeval.evalOnce(transactionData.flatMap(_.cast[Signed]))

  def stringRepr(totalBlockId: ByteStr): String = s"MicroBlock(${totalBlockId.trim} -> ${reference.trim}, txs=${transactionData.size})"
}

object MicroBlock extends ScorexLogging {
  def buildAndSign(
      version: Byte,
      generator: KeyPair,
      transactionData: Seq[Transaction],
      reference: BlockId,
      totalResBlockSig: BlockId
  ): Either[ValidationError, MicroBlock] =
    MicroBlock(version, generator.publicKey, transactionData, reference, totalResBlockSig, ByteStr.empty).validate
      .map(_.sign(generator.privateKey))

  def parseBytes(bytes: Array[Byte]): Try[MicroBlock] =
    MicroBlockSerializer
      .parseBytes(bytes)
      .flatMap(_.validateToTry)
      .recoverWith {
        case t: Throwable =>
          log.error("Error when parsing microblock", t)
          Failure(t)
      }

  def validateReferenceLength(version: Byte, length: Int): Boolean =
    length == Block.referenceLength(version)
}
