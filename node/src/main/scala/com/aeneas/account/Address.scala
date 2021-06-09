package com.aeneas.account

import java.nio.ByteBuffer

import com.google.common.cache.{Cache, CacheBuilder}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.Base58
import com.aeneas.crypto
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.TxValidationError.InvalidAddress
import com.aeneas.utils.{ScorexLogging, base58Length}
import play.api.libs.json._
import scorex.crypto.hash.Blake2b256

sealed trait Address extends AddressOrAlias {
  lazy val stringRepr: String = Address.Prefix + Base58.encode(bytes)
}

//noinspection ScalaDeprecation
object Address extends ScorexLogging {
  val Prefix: String           = "Æx"
  val AddressVersion: Byte     = 1
  val ChecksumLength: Int      = 4
  val HashLength: Int          = 32
  val AddressLength: Int       = 1 + 1/*2 for random UTF symbol in chainId*/ + HashLength + ChecksumLength
  val AddressLengthOld: Int       = 1 + HashLength + ChecksumLength//without chain Id
  val AddressStringLength: Int = base58Length(AddressLength)
  val AddressStringLengthOld: Int = base58Length(AddressLengthOld)

  private[this] val publicKeyBytesCache: Cache[(ByteStr, Byte), Address] = CacheBuilder
    .newBuilder()
    .softValues()
    .maximumSize(200000)
    .build()

  private[this] val bytesCache: Cache[ByteStr, Either[InvalidAddress, Address]] = CacheBuilder
    .newBuilder()
    .softValues()
    .maximumSize(200000)
    .build()

  def fromPublicKey(publicKey: PublicKey, chainId: Byte = scheme.chainId): Address = {
    publicKeyBytesCache.get(
      (publicKey, chainId), { () =>
        val bytes = chainId match {
          case Byte.MinValue =>
            def bytesWithVersion: Array[Byte] = AddressVersion +: publicKey.arr
          /*
                  ByteBuffer
                        .allocate( 1 + HashLength)
                        .put(AddressVersion)
                        .put(crypto.secureHash(publicKey.arr), 0, HashLength)
                        .array()
          */
            bytesWithVersion ++ calcCheckSum(bytesWithVersion)
          case _ =>
            val withoutChecksum: Array[Byte] = ByteBuffer
              .allocate(1 + 1 + HashLength)
              .put(AddressVersion)
              .put(chainId)
              .put(crypto.secureHash(publicKey.arr), 0, HashLength)
              .array()
            ByteBuffer
              .allocate(AddressLength)
              .put(withoutChecksum)
              .put(calcCheckSum(withoutChecksum), 0, ChecksumLength)
              .array()
        }


        createUnsafe(bytes)
      }
    )
  }

  def fromBytes(addressBytes: Array[Byte], chainId: Byte = scheme.chainId): Either[InvalidAddress, Address] = {
    bytesCache.get(
      ByteStr(addressBytes), { () =>
        Either
          .cond(
            addressBytes.length == Address.AddressLength || addressBytes.length == Address.AddressLengthOld,
            (),
            InvalidAddress(s"Wrong addressBytes length: expected: ${Address.AddressLength} or ${Address.AddressLengthOld}, actual: ${addressBytes.length}")
          )
          .flatMap {
            _ =>
              if (addressBytes.length == Address.AddressLength){
                val Array(version, network, _*) = addressBytes

                (for {
                  _ <- Either.cond(version == AddressVersion, (), s"Unknown address version: $version")
                  _ <- Either.cond(
                    network == chainId,
                    (),
                    s"Data from other network: expected: ${DefaultAddressScheme.chainId}, $chainId(${chainId.toChar}), actual: $network(${network.toChar})"
                  )
                  checkSum          = addressBytes.takeRight(ChecksumLength)
                  checkSumGenerated = calcCheckSum(addressBytes.dropRight(ChecksumLength))
                  _ <- Either.cond(java.util.Arrays.equals(checkSum, checkSumGenerated), (), s"Bad address checksum:${checkSum.mkString("Array(", ", ", ")")}")
                } yield createUnsafe(addressBytes)).left.map(err => InvalidAddress(err))
              } else {
                val Array(version, _*) = addressBytes

                (for {
                  _ <- Either.cond(version == AddressVersion, (), s"Unknown address version: $version")
                  checkSum          = addressBytes.takeRight(ChecksumLength)
                  checkSumGenerated = calcCheckSum(addressBytes.dropRight(ChecksumLength))
                  _ <- Either.cond(java.util.Arrays.equals(checkSum, checkSumGenerated), (), s"Bad address checksum")
                } yield createUnsafe(addressBytes)).left.map(err => InvalidAddress(err))
              }
          }
      }
    )
  }

  def fromString(addressStr: String): Either[ValidationError, Address] = {
    log.debug(s"addressStr: ${addressStr}")
    val base58String = if (addressStr.startsWith(Prefix)) addressStr.drop(Prefix.length) else addressStr
    for {
      _ <- Either.cond(
        base58String.length <= AddressStringLengthOld,
        (),
        InvalidAddress(s"Wrong address string length: max=$AddressStringLengthOld, actual: ${base58String.length}")
      )
      byteArray <- Base58.tryDecodeWithLimit(base58String).toEither.left.map(ex => InvalidAddress(s"Unable to decode base58: ${ex.getMessage}"))
      address   <- fromBytes(byteArray)
    } yield address
  }

  def calcCheckSum(bytes: Array[Byte]): Array[Byte] = Blake2b256.hash(bytes).take(ChecksumLength)

  implicit val jsonFormat: Format[Address] = Format[Address](
    Reads(jsValue => fromString(jsValue.as[String]).fold(err => JsError(err.toString), JsSuccess(_))),
    Writes(addr => JsString(addr.stringRepr))
  )

  @inline
  private[this] def scheme: AddressScheme = AddressScheme.current

  // Optimization, should not be used externally
  private[aeneas] def createUnsafe(addressBytes: Array[Byte]): Address = new Address {
    override val bytes: Array[Byte] = addressBytes
    override val chainId: Byte = if (addressBytes.length == AddressLength)addressBytes(1)else DefaultAddressScheme.chainId
  }
}
