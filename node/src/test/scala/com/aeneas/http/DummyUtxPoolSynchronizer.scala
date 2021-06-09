package com.aeneas.http
import com.aeneas.lang.ValidationError
import com.aeneas.network.UtxPoolSynchronizer
import com.aeneas.transaction.Transaction
import com.aeneas.transaction.smart.script.trace.TracedResult
import io.netty.channel.Channel

object DummyUtxPoolSynchronizer {
  val accepting: UtxPoolSynchronizer = new UtxPoolSynchronizer {
    override def tryPublish(tx: Transaction, source: Channel): Unit               = {}
    override def publish(tx: Transaction): TracedResult[ValidationError, Boolean] = TracedResult(Right(true))
  }

  def rejecting(error: Transaction => ValidationError): UtxPoolSynchronizer = new UtxPoolSynchronizer {
    override def tryPublish(tx: Transaction, source: Channel): Unit               = {}
    override def publish(tx: Transaction): TracedResult[ValidationError, Boolean] = TracedResult(Left(error(tx)))
  }
}
