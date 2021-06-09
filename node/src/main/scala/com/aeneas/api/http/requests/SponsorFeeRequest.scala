package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.assets.SponsorFeeTransaction
import com.aeneas.transaction.{AssetIdStringLength, Proofs}
import play.api.libs.json.{Format, Json}

object SponsorFeeRequest {
  implicit val unsignedSponsorRequestFormat: Format[SponsorFeeRequest]     = Json.format
  implicit val signedSponsorRequestFormat: Format[SignedSponsorFeeRequest] = Json.format
}

case class SponsorFeeRequest(
    version: Option[Byte],
    sender: String,
    assetId: String,
    minSponsoredAssetFee: Option[Long],
    fee: Long,
    timestamp: Option[Long] = None
)

case class SignedSponsorFeeRequest(
    version: Option[Byte],
    senderPublicKey: String,
    assetId: String,
    minSponsoredAssetFee: Option[Long],
    fee: Long,
    timestamp: Long,
    proofs: Proofs
) {
  def toTx: Either[ValidationError, SponsorFeeTransaction] =
    for {
      _sender <- PublicKey.fromBase58String(senderPublicKey)
      _asset  <- parseBase58(assetId, "invalid.assetId", AssetIdStringLength).map(IssuedAsset)
      t       <- SponsorFeeTransaction.create(version.getOrElse(1.toByte), _sender, _asset, minSponsoredAssetFee.filterNot(_ == 0), fee, timestamp, proofs)
    } yield t
}
