package com.aeneas.transaction.validation.impl

import cats.data.Validated
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.validation.{TxValidator, ValidatedV}
import com.aeneas.transaction.{TxValidationError, TxVersion}

object IssueTxValidator extends TxValidator[IssueTransaction] {
  override def validate(tx: IssueTransaction): ValidatedV[IssueTransaction] = {
    def assetDecimals(decimals: Byte): ValidatedV[Byte] = {
      Validated
        .condNel(
          decimals >= 0 && decimals <= IssueTransaction.MaxAssetDecimals,
          decimals,
          TxValidationError.TooBigArray
        )
    }

    import tx._
    V.seq(tx)(
      V.positiveAmount(quantity, "assets"),
      V.assetName(tx.name),
      V.assetDescription(tx.description),
      assetDecimals(decimals),
      V.fee(fee),
      V.cond(version > TxVersion.V1 || script.isEmpty, GenericError("Script not supported")),
      V.cond(script.forall(_.isInstanceOf[ExprScript]), GenericError(s"Asset can only be assigned with Expression script, not Contract"))
    )
  }
}
