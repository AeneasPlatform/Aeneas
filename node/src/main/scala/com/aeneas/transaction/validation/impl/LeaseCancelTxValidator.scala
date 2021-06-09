package com.aeneas.transaction.validation.impl

import cats.data.ValidatedNel
import com.aeneas.crypto
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.transaction.lease.LeaseCancelTransaction
import com.aeneas.transaction.validation.TxValidator

object LeaseCancelTxValidator extends TxValidator[LeaseCancelTransaction] {
  override def validate(tx: LeaseCancelTransaction): ValidatedNel[ValidationError, LeaseCancelTransaction] = {
    import tx._
    V.seq(tx)(
      V.fee(fee),
      V.cond(leaseId.arr.length == crypto.DigestLength, GenericError("Lease transaction id is invalid"))
    )
  }
}
