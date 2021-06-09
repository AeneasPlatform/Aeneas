package com.aeneas.protobuf.transaction
import com.google.protobuf.ByteString
import com.wavesplatform.protobuf.Amount
import com.aeneas.transaction.Asset
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.aeneas.protobuf.utils.PBImplicitConversions._

object PBAmounts {
  def toPBAssetId(asset: Asset): ByteString = asset match {
    case Asset.IssuedAsset(id) => id.toByteString
    case Asset.Waves           => ByteString.EMPTY
  }

  def toVanillaAssetId(byteStr: ByteString): Asset = {
    if (byteStr.isEmpty) Waves
    else IssuedAsset(byteStr.toByteStr)
  }

  def fromAssetAndAmount(asset: Asset, amount: Long): Amount =
    Amount(toPBAssetId(asset), amount)

  def toAssetAndAmount(value: Amount): (Asset, Long) =
    (toVanillaAssetId(value.assetId), value.amount)
}
