package com.aeneas.transaction.validation.impl

import cats.data.ValidatedNel
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.PaymentTransaction
import com.aeneas.transaction.validation.TxValidator

object PaymentTxValidator extends TxValidator[PaymentTransaction] {
  override def validate(transaction: PaymentTransaction): ValidatedNel[ValidationError, PaymentTransaction] = {
    import transaction._
    V.seq(transaction)(
      V.fee(fee),
      V.positiveAmount(amount, "waves"),
      V.noOverflow(fee, amount),
      V.addressChainId(recipient, chainId)
    )
  }
}
