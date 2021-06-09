package com.aeneas.transaction.validation.impl

import cats.implicits._
import com.aeneas.transaction.TxAmount
import com.aeneas.transaction.TxValidationError.NegativeMinFee
import com.aeneas.transaction.assets.SponsorFeeTransaction
import com.aeneas.transaction.validation.{TxValidator, ValidatedV}

object SponsorFeeTxValidator extends TxValidator[SponsorFeeTransaction] {
  override def validate(tx: SponsorFeeTransaction): ValidatedV[SponsorFeeTransaction] = {
    import tx._
    V.seq(tx)(
      checkMinSponsoredAssetFee(minSponsoredAssetFee).toValidatedNel,
      V.fee(fee)
    )
  }

  def checkMinSponsoredAssetFee(minSponsoredAssetFee: Option[TxAmount]): Either[NegativeMinFee, Unit] =
    Either.cond(minSponsoredAssetFee.forall(_ > 0), (), NegativeMinFee(minSponsoredAssetFee.get, "asset"))
}
