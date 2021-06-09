package com.aeneas.account

import java.util
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.Base58
import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.wallet.Wallet
import com.aeneas.{crypto, utils}
import com.google.common.primitives.Ints
import play.api.libs.json.{Format, Json, Writes}
import scorex.crypto.hash.{Blake2b256, Sha256}

import scala.util.{Failure, Success, Try}

final class KeyPair(val seed1: Array[Byte]) {
  lazy val seed = {
    println(s"isOld = $isOld")
    if (isOld) seed1.drop(2) else seed1
  }
  lazy val isOld = {
    if (seed1.length >=2 )println(s"${seed1(0)} == ${Byte.MinValue} && ${seed1(1)} == ${(Byte.MinValue + 1.toByte).toByte}")
    println(seed1.mkString("Array(", ", ", ")"))
    seed1.length > 2 && seed1(0) == Byte.MinValue && seed1(1) == (Byte.MinValue + 1.toByte).toByte
  }
  lazy val (PrivateKey(privateKey), PublicKey(publicKey)) = crypto.createKeyPair(seed)

  override def equals(obj: Any): Boolean = obj match {
    case kp: KeyPair => util.Arrays.equals(kp.seed, seed)
    case _           => false
  }

  private lazy val hc          = util.Arrays.hashCode(seed)
  override def hashCode(): Int = hc
}

object KeyPair {
  def apply(seed: ByteStr): KeyPair     = new KeyPair(seed.arr)
  def apply(seed: Array[Byte]): KeyPair = new KeyPair(seed)

    def fromSeed(base58: String): Either[GenericError, KeyPair] = Base58.tryDecodeWithLimit(base58) match {
      case Success(x) => Right(KeyPair(ByteStr(x)))
      case Failure(e) => Left(GenericError(s"Unable to get a private key from the seed '$base58': ${e.getMessage}"))
    }

  implicit class KeyPairImplicitOps(private val kp: KeyPair) extends AnyVal {
    def toAddress: Address                = if (kp.isOld) kp.publicKey.toAddress(Byte.MinValue) else kp.publicKey.toAddress
    def toAddress(chainId: Byte): Address = if (kp.isOld) kp.publicKey.toAddress(Byte.MinValue) else kp.publicKey.toAddress(chainId)
  }

  implicit val jsonFormat: Format[KeyPair] = Format(
    utils.byteStrFormat.map(KeyPair(_)),
    Writes(v => Json.obj("seed" -> Base58.encode(v.seed), "publicKey" -> v.publicKey, "privateKey" -> v.privateKey))
  )
}
