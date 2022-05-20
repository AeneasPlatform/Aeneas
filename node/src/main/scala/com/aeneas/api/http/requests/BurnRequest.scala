package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.assets.BurnTransaction
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class BurnRequest(
    version: Option[Byte],
    sender: Option[String],
    senderPublicKey: Option[String],
    asset: IssuedAsset,
    quantity: Long,
    fee: Long,
    timestamp: Option[Long],
    signature: Option[ByteStr],
    proofs: Option[Proofs]
) extends TxBroadcastRequest {
  def toTxFrom(sender: PublicKey): Either[ValidationError, BurnTransaction] =
    for {
      validProofs <- toProofs(signature, proofs)
      tx <- BurnTransaction.create(
        version.getOrElse(defaultVersion),
        sender,
        asset,
        quantity,
        fee,
        timestamp.getOrElse(defaultTimestamp),
        validProofs
      )
    } yield tx
}

object BurnRequest {
  import com.aeneas.utils.byteStrFormat
  implicit val jsonFormat: Format[BurnRequest] = Format(
    ((JsPath \ "version").readNullable[Byte] and
      (JsPath \ "sender").readNullable[String] and
      (JsPath \ "senderPublicKey").readNullable[String] and
      (JsPath \ "assetId").read[IssuedAsset] and
      (JsPath \ "quantity").read[Long].orElse((JsPath \ "amount").read[Long]) and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").readNullable[Long] and
      (JsPath \ "signature").readNullable[ByteStr] and
      (JsPath \ "proofs").readNullable[Proofs])(BurnRequest.apply _),
    Json.writes[BurnRequest]
  )
}
