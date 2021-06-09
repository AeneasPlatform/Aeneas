package com.aeneas.transaction.validation.impl

import cats.data.ValidatedNel
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.TxValidationError
import com.aeneas.transaction.lease.LeaseTransaction
import com.aeneas.transaction.validation.TxValidator

object LeaseTxValidator extends TxValidator[LeaseTransaction] {
  override def validate(tx: LeaseTransaction): ValidatedNel[ValidationError, LeaseTransaction] = {
    import tx._
    V.seq(tx)(
      V.fee(fee),
      V.cond(amount > 0, TxValidationError.NonPositiveAmount(amount, "waves")),
      V.noOverflow(amount, fee),
      V.cond(sender.toAddress != recipient, TxValidationError.ToSelf),
      V.addressChainId(recipient, chainId)
    )
  }
}
