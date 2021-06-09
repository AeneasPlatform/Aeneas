package com.aeneas.transaction.validation.impl

import cats.data.ValidatedNel
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.transfer.TransferTransaction
import com.aeneas.transaction.validation.TxValidator

object TransferTxValidator extends TxValidator[TransferTransaction] {
  override def validate(transaction: TransferTransaction): ValidatedNel[ValidationError, TransferTransaction] = {
    import transaction._
    V.seq(transaction)(
      V.fee(fee),
      V.positiveAmount(amount, assetId.maybeBase58Repr.getOrElse("waves")),
      V.transferAttachment(attachment),
      V.addressChainId(recipient, chainId)
    )
  }
}
