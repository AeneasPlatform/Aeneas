package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.assets.exchange.{ExchangeTransaction, Order}
import com.aeneas.transaction.{Proofs, TxAmount, TxTimestamp, TxVersion}
import play.api.libs.json.{Format, Json}

case class ExchangeRequest(
    order1: Order,
    order2: Order,
    amount: Long,
    price: Long,
    buyMatcherFee: Long,
    sellMatcherFee: Long,
    version: Option[TxVersion] = None,
    sender: Option[String] = None,
    senderPublicKey: Option[String] = None,
    fee: Option[TxAmount] = None,
    timestamp: Option[TxTimestamp] = None,
    signature: Option[ByteStr] = None,
    proofs: Option[Proofs] = None
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, ExchangeTransaction] =
    for {
      validProofs <- toProofs(signature, proofs)
      tx <- ExchangeTransaction.create(
        version.getOrElse(1.toByte),
        order1,
        order2,
        amount,
        price,
        buyMatcherFee,
        sellMatcherFee,
        fee.getOrElse(0L),
        timestamp.getOrElse(0L),
        validProofs
      )
    } yield tx
}

object ExchangeRequest {
  implicit val jsonFormat: Format[ExchangeRequest] = Json.format
}
