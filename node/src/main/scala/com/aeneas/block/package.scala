package com.aeneas

import cats.syntax.either._
import com.aeneas.account.PrivateKey
import com.aeneas.block.Block.{TransactionProof, TransactionsMerkleTree}
import com.aeneas.common.merkle.Merkle._
import com.aeneas.block.validation.Validators._
import com.aeneas.common.state.ByteStr
import com.aeneas.protobuf.transaction.PBTransactions
import com.aeneas.settings.GenesisSettings
import com.aeneas.transaction.Transaction

import scala.util.Try

package object block {

  // Validation
  private[block] implicit class BlockValidationOps(block: Block) {
    def validate: Validation[Block]                             = validateBlock(block)
    def validateToTry: Try[Block]                               = toTry(validateBlock(block))
    def validateGenesis(gs: GenesisSettings): Validation[Block] = validateGenesisBlock(block, gs)
  }

  private[block] implicit class MicroBlockValidationOps(microBlock: MicroBlock) {
    def validate: Validation[MicroBlock] = validateMicroBlock(microBlock)
    def validateToTry: Try[MicroBlock]   = toTry(validateMicroBlock(microBlock))
  }

  private def toTry[A](result: Validation[A]): Try[A] = result.leftMap(ge => new IllegalArgumentException(ge.err)).toTry

  // Sign
  private[block] implicit class BlockSignOps(block: Block) {
    def sign(signer: PrivateKey): Block = block.copy(signature = crypto.sign(signer, block.bodyBytes()))
  }

  private[block] implicit class MicroBlockSignOps(microBlock: MicroBlock) {
    def sign(signer: PrivateKey): MicroBlock = microBlock.copy(signature = crypto.sign(signer, microBlock.bytesWithoutSignature()))
  }

  def transactionProof(transaction: Transaction, transactionData: Seq[Transaction]): Option[TransactionProof] =
    transactionData.indexWhere(transaction.id() == _.id()) match {
      case -1  => None
      case idx => Some(TransactionProof(transaction.id(), idx, mkProofs(idx, mkMerkleTree(transactionData)).reverse))
    }

  implicit class MerkleTreeOps(private val levels: TransactionsMerkleTree) extends AnyVal {
    def transactionsRoot: ByteStr = {
      require(levels.nonEmpty && levels.head.nonEmpty, "Invalid merkle tree")
      ByteStr(levels.head.head)
    }
  }

  def mkMerkleTree(txs: Seq[Transaction]): TransactionsMerkleTree = mkLevels(txs.map(PBTransactions.protobuf(_).toByteArray))

  def mkTransactionsRoot(version: Byte, transactionData: Seq[Transaction]): ByteStr =
    if (version < Block.ProtoBlockVersion) ByteStr.empty
    else mkLevels(transactionData.map(PBTransactions.protobuf(_).toByteArray)).transactionsRoot
}
