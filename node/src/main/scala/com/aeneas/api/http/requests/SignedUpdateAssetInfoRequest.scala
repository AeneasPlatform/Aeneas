package com.aeneas.api.http.requests

import cats.implicits._
import com.aeneas.account.PublicKey
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.aeneas.transaction.assets.UpdateAssetInfoTransaction
import com.aeneas.transaction.{AssetIdStringLength, Proofs, TxAmount, TxTimestamp, TxVersion}
import play.api.libs.json.{Format, Json}

case class SignedUpdateAssetInfoRequest(
    version: TxVersion,
    chainId: Byte,
    senderPublicKey: String,
    assetId: String,
    name: String,
    description: String,
    timestamp: TxTimestamp,
    fee: TxAmount,
    feeAssetId: Option[String],
    proofs: Proofs
) {

  def toTx: Either[ValidationError, UpdateAssetInfoTransaction] =
    for {
      _sender  <- PublicKey.fromBase58String(senderPublicKey)
      _assetId <- parseBase58(assetId, "invalid.assetId", AssetIdStringLength)
      _feeAssetId <- feeAssetId
        .traverse(parseBase58(_, "invalid.assetId", AssetIdStringLength).map(IssuedAsset))
        .map(_ getOrElse Waves)
      tx <- UpdateAssetInfoTransaction
        .create(version, _sender, _assetId, name, description, timestamp, fee, _feeAssetId, proofs, chainId)
    } yield tx

}

object SignedUpdateAssetInfoRequest {
  implicit val format: Format[SignedUpdateAssetInfoRequest] = Json.format
}
