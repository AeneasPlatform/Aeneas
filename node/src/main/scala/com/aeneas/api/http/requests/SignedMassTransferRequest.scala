package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.transfer.MassTransferTransaction.Transfer
import com.aeneas.transaction.transfer._
import play.api.libs.functional.syntax._
import play.api.libs.json._

object SignedMassTransferRequest {
  implicit val jsonFormat: Format[SignedMassTransferRequest] = Format(
    (
      (JsPath \ "version").readNullable[Byte] and
        (JsPath \ "senderPublicKey").read[String] and
        (JsPath \ "assetId").readNullable[String] and
        (JsPath \ "transfers").read[List[Transfer]] and
        (JsPath \ "fee").read[Long] and
        (JsPath \ "timestamp").read[Long] and
        (JsPath \ "attachment").readWithDefault(ByteStr.empty) and
        (JsPath \ "proofs").read[Proofs]
    )(SignedMassTransferRequest.apply _),
    Json.writes[SignedMassTransferRequest].transform((jsobj: JsObject) => jsobj + ("type" -> JsNumber(MassTransferTransaction.typeId.toInt)))
  )
}

case class SignedMassTransferRequest(
    version: Option[Byte],
    senderPublicKey: String,
    assetId: Option[String],
    transfers: List[Transfer],
    fee: Long,
    timestamp: Long,
    attachment: ByteStr,
    proofs: Proofs
) {
  def toTx: Either[ValidationError, MassTransferTransaction] =
    for {
      _sender    <- PublicKey.fromBase58String(senderPublicKey)
      _assetId   <- parseBase58ToAsset(assetId.filter(_.length > 0), "invalid.assetId")
      _transfers <- MassTransferTransaction.parseTransfersList(transfers)
      t          <- MassTransferTransaction.create(version.getOrElse(1.toByte), _sender, _assetId, _transfers, fee, timestamp, attachment, proofs)
    } yield t
}
