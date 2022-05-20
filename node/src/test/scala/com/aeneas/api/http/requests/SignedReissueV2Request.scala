package com.aeneas.api.http.requests

import cats.implicits._
import com.aeneas.account.{AddressScheme, PublicKey}
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.assets.ReissueTransaction
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

case class SignedReissueV2Request(
    senderPublicKey: String,
    assetId: String,
    quantity: Long,
    reissuable: Boolean,
    fee: Long,
    timestamp: Long,
    proofs: List[String]
) {
  def toTx: Either[ValidationError, ReissueTransaction] =
    for {
      _sender <- PublicKey.fromBase58String(senderPublicKey)
      chainId = AddressScheme.current.chainId
      _proofBytes <- proofs.traverse(s => parseBase58(s, "invalid proof", Proofs.MaxProofStringSize))
      _proofs     <- Proofs.create(_proofBytes)
      _assetId    <- parseBase58ToIssuedAsset(assetId)
      _t          <- ReissueTransaction.create(2.toByte, _sender, _assetId, quantity, reissuable, fee, timestamp, _proofs)
    } yield _t
}

object SignedReissueV2Request {
  implicit val assetReissueRequestReads: Reads[SignedReissueV2Request] = (
    (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "assetId").read[String] and
      (JsPath \ "quantity").read[Long] and
      (JsPath \ "reissuable").read[Boolean] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "proofs").read[List[ProofStr]]
  )(SignedReissueV2Request.apply _)
}
