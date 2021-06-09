package com.aeneas.transaction.validation.impl

import cats.data.Validated
import com.aeneas.account.Alias
import com.aeneas.transaction.CreateAliasTransaction
import com.aeneas.transaction.validation.{TxValidator, ValidatedV}

object CreateAliasTxValidator extends TxValidator[CreateAliasTransaction] {
  override def validate(tx: CreateAliasTransaction): ValidatedV[CreateAliasTransaction] = {
    import tx._
    V.seq(tx)(
      V.fee(fee),
      Validated.fromEither(Alias.createWithChainId(aliasName, chainId)).toValidatedNel.map((_: Alias) => tx)
    )
  }
}
