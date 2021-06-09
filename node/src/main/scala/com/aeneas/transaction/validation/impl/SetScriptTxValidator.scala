package com.aeneas.transaction.validation.impl

import com.aeneas.transaction.smart.SetScriptTransaction
import com.aeneas.transaction.validation.{TxValidator, _}

object SetScriptTxValidator extends TxValidator[SetScriptTransaction] {
  override def validate(tx: SetScriptTransaction): ValidatedV[SetScriptTransaction] =
    V.fee(tx.fee).map(_ => tx)
}
