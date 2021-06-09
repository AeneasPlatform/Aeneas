package com.aeneas.transaction.validation.impl

import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.transaction.transfer.MassTransferTransaction
import com.aeneas.transaction.transfer.MassTransferTransaction.MaxTransferCount
import com.aeneas.transaction.validation.{TxValidator, ValidatedV}

object MassTransferTxValidator extends TxValidator[MassTransferTransaction] {
  override def validate(tx: MassTransferTransaction): ValidatedV[MassTransferTransaction] = {
    import tx._
    V.seq(tx)(
      V.noOverflow(fee +: transfers.map(_.amount): _*),
      V.cond(transfers.length <= MaxTransferCount, GenericError(s"Number of transfers ${transfers.length} is greater than $MaxTransferCount")),
      V.transferAttachment(attachment),
      V.cond(transfers.forall(_.amount >= 0), GenericError("One of the transfers has negative amount")),
      V.fee(fee),
      V.chainIds(chainId, transfers.map(_.address.chainId): _*)
    )
  }
}
