package com.aeneas.transaction.serialization.impl

import java.nio.ByteBuffer

import com.google.common.primitives.{Bytes, Longs}
import com.aeneas.account.Address
import com.aeneas.common.state.ByteStr
import com.aeneas.serialization._
import com.aeneas.transaction.GenesisTransaction
import play.api.libs.json.{JsObject, Json}

import scala.util.Try

object GenesisTxSerializer {
  val BaseLength: Int = Address.AddressLength + Longs.BYTES * 2
  val BaseLengthOld: Int = Address.AddressLengthOld + Longs.BYTES * 2

  def toJson(tx: GenesisTransaction): JsObject = {
    import tx._
    Json.obj(
      "type"      -> builder.typeId,
      "id"        -> id().toString,
      "fee"       -> 0,
      "timestamp" -> timestamp,
      "signature" -> signature.toString,
      "recipient" -> recipient.stringRepr,
      "amount"    -> amount
    )
  }

  def toBytes(tx: GenesisTransaction): Array[Byte] = {
    import tx._
    val typeBytes      = Array(builder.typeId)
    val timestampBytes = Longs.toByteArray(timestamp)
    val rcpBytes       = recipient.bytes
    val amountBytes    = Longs.toByteArray(amount)
    require(rcpBytes.length == Address.AddressLength || rcpBytes.length == Address.AddressLengthOld)
    val res = Bytes.concat(typeBytes, timestampBytes, rcpBytes, amountBytes)
    require(res.length == BaseLength + 1 || res.length == BaseLengthOld + 1)
    res
  }

  def parseBytes(bytes: Array[Byte]): Try[GenesisTransaction] = Try {
    val buf = ByteBuffer.wrap(bytes)
    require(buf.getByte == GenesisTransaction.typeId, "transaction type mismatch")
    val timestamp = buf.getLong
    val recipient = buf.getAddress
    val amount    = buf.getLong
    GenesisTransaction(recipient, amount, timestamp, ByteStr(GenesisTransaction.generateSignature(recipient, amount, timestamp)), recipient.chainId)
  }
}
