package com.aeneas.events

import com.aeneas.lang.ValidationError
import com.aeneas.state.Diff
import com.aeneas.transaction.Transaction

sealed trait UtxEvent
object UtxEvent {
  final case class TxAdded(tx: Transaction, diff: Diff) extends UtxEvent
  final case class TxRemoved(tx: Transaction, reason: Option[ValidationError]) extends UtxEvent
}
