package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.assets.BurnTransaction
import play.api.libs.functional.syntax._
import play.api.libs.json._

object SignedBurnV1Request {
  implicit val reads: Reads[SignedBurnV1Request] = (
    (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "assetId").read[String] and
      (JsPath \ "quantity").read[Long].orElse((JsPath \ "amount").read[Long]) and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "signature").read[String]
  )(SignedBurnV1Request.apply _)

  implicit val writes: Writes[SignedBurnV1Request] = Json.writes[SignedBurnV1Request]
}

case class SignedBurnV1Request(
    senderPublicKey: String,
    assetId: String,
    quantity: Long,
    fee: Long,
    timestamp: Long,
    signature: String
) {

  def toTx: Either[ValidationError, BurnTransaction] =
    for {
      _sender    <- PublicKey.fromBase58String(senderPublicKey)
      _assetId   <- parseBase58ToIssuedAsset(assetId)
      _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
      _t         <- BurnTransaction.create(1.toByte, _sender, _assetId, quantity, fee, timestamp, Proofs(_signature))
    } yield _t
}
