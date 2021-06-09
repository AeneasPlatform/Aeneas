package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.assets.ReissueTransaction
import play.api.libs.json.{Format, Json}

object SignedReissueV1Request {
  implicit val assetReissueRequestReads: Format[SignedReissueV1Request] = Json.format
}

case class SignedReissueV1Request(
    senderPublicKey: String,
    assetId: String,
    quantity: Long,
    reissuable: Boolean,
    fee: Long,
    timestamp: Long,
    signature: String
) {
  def toTx: Either[ValidationError, ReissueTransaction] =
    for {
      _sender    <- PublicKey.fromBase58String(senderPublicKey)
      _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
      _assetId   <- parseBase58ToIssuedAsset(assetId)
      _t         <- ReissueTransaction.create(1.toByte, _sender, _assetId, quantity, reissuable, fee, timestamp, Proofs(_signature))
    } yield _t
}
