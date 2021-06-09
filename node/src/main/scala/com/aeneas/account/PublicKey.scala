package com.aeneas.account

import com.google.common.collect.Interners
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.Base58
import com.aeneas.crypto._
import com.aeneas.transaction.TxValidationError.InvalidAddress
import com.aeneas.utils.base58Length
import play.api.libs.json.{Format, Writes}
import supertagged._

object PublicKey extends TaggedType[ByteStr] {
  private[this] val interner = Interners.newWeakInterner[PublicKey]()

  val KeyStringLength: Int = base58Length(KeyLength)

  def apply(publicKey: ByteStr): PublicKey = {
    require(publicKey.arr.length == KeyLength, s"invalid public key length: ${publicKey.arr.length}")
    interner.intern(publicKey @@ this)
  }

  def apply(publicKey: Array[Byte]): PublicKey =
    apply(ByteStr(publicKey))

  def fromBase58String(base58: String): Either[InvalidAddress, PublicKey] =
    (for {
      _     <- Either.cond(base58.length <= KeyStringLength, (), "Bad public key string length")
      bytes <- Base58.tryDecodeWithLimit(base58).toEither.left.map(ex => s"Unable to decode base58: ${ex.getMessage}")
    } yield PublicKey(bytes)).left.map(err => InvalidAddress(s"Invalid sender: $err"))

  def unapply(arg: Array[Byte]): Option[PublicKey] =
    Some(apply(arg))

  implicit class PublicKeyImplicitOps(private val pk: PublicKey) extends AnyVal {
    def toAddress: Address                = Address.fromPublicKey(pk)
    def toAddress(chainId: Byte): Address = Address.fromPublicKey(pk, chainId)
  }

  implicit lazy val jsonFormat: Format[PublicKey] = Format[PublicKey](
    com.aeneas.utils.byteStrFormat.map(this.apply),
    Writes(pk => com.aeneas.utils.byteStrFormat.writes(pk))
  )
}
