package com.aeneas

import cats.data.ValidatedNel
import com.aeneas.account.PrivateKey
import com.aeneas.block.{Block, MicroBlock}
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.state.Diff
import com.aeneas.transaction.validation.TxValidator
import com.aeneas.utils.base58Length

package object transaction {
  val AssetIdLength: Int       = com.aeneas.crypto.DigestLength
  val AssetIdStringLength: Int = base58Length(AssetIdLength)

  type DiscardedBlocks       = Seq[(Block, ByteStr)]
  type DiscardedMicroBlocks  = Seq[(MicroBlock, Diff)]
  type AuthorizedTransaction = Authorized with Transaction

  type TxType = Byte

  type TxVersion = Byte
  object TxVersion {
    val V1: TxVersion = 1.toByte
    val V2: TxVersion = 2.toByte
    val V3: TxVersion = 3.toByte
  }
  type TxAmount    = Long
  type TxTimestamp = Long
  type TxByteArray = Array[Byte]

  implicit class TransactionValidationOps[T <: Transaction: TxValidator](tx: T) {
    def validatedNel: ValidatedNel[ValidationError, T] = implicitly[TxValidator[T]].validate(tx)
    def validatedEither: Either[ValidationError, T]    = this.validatedNel.toEither.left.map(_.head)
  }

  implicit class TransactionSignOps[T](tx: T)(implicit sign: (T, PrivateKey) => T) {
    def signWith(privateKey: PrivateKey): T = sign(tx, privateKey)
  }
}
