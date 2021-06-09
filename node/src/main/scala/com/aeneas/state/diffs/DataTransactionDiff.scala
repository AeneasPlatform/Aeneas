package com.aeneas.state.diffs

import com.aeneas.lang.ValidationError
import com.aeneas.state._
import com.aeneas.transaction.DataTransaction

object DataTransactionDiff {

  def apply(blockchain: Blockchain)(tx: DataTransaction): Either[ValidationError, Diff] = {
    val sender = tx.sender.toAddress
    Right(
      Diff(
        tx,
        portfolios = Map(sender -> Portfolio(-tx.fee, LeaseBalance.empty, Map.empty)),
        accountData = Map(sender -> AccountDataInfo(tx.data.map(item => item.key -> item).toMap)),
        scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)
      )
    )
  }
}
