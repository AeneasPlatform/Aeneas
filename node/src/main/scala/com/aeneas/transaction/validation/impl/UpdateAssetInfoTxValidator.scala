package com.aeneas.transaction.validation.impl

import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.assets.UpdateAssetInfoTransaction
import com.aeneas.transaction.validation.{TxValidator, ValidatedV}
import com.aeneas.transaction.{Asset, TxValidationError}
import com.aeneas.utils.StringBytes

object UpdateAssetInfoTxValidator extends TxValidator[UpdateAssetInfoTransaction] {
  override def validate(tx: UpdateAssetInfoTransaction): ValidatedV[UpdateAssetInfoTransaction] =
    V.seq(tx)(
      V.cond(UpdateAssetInfoTransaction.supportedVersions(tx.version), TxValidationError.UnsupportedVersion(tx.version)),
      V.fee(tx.feeAmount),
      V.asset[IssuedAsset](tx.assetId),
      V.asset[Asset](tx.feeAsset),
      V.assetName(tx.name.toByteString),
      V.assetDescription(tx.description.toByteString)
    )
}
