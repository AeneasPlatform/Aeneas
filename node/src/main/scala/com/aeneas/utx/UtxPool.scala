package com.aeneas.utx

import com.aeneas.account.Address
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.mining.MultiDimensionalMiningConstraint
import com.aeneas.state.Portfolio
import com.aeneas.transaction._
import com.aeneas.transaction.smart.script.trace.TracedResult
import com.aeneas.utx.UtxPool.PackStrategy

import scala.concurrent.duration.FiniteDuration

trait UtxPool extends AutoCloseable {
  def putIfNew(tx: Transaction): TracedResult[ValidationError, Boolean]
  def removeAll(txs: Iterable[Transaction]): Unit
  def spendableBalance(addr: Address, assetId: Asset): Long
  def pessimisticPortfolio(addr: Address): Portfolio
  def all: Seq[Transaction]
  def size: Int
  def transactionById(transactionId: ByteStr): Option[Transaction]
  def packUnconfirmed(
      rest: MultiDimensionalMiningConstraint,
      strategy: PackStrategy = PackStrategy.Unlimited,
      cancelled: () => Boolean = () => false
  ): (Option[Seq[Transaction]], MultiDimensionalMiningConstraint)
  def nextMicroBlockSize(): Option[Int]
}

object UtxPool {
  sealed trait PackStrategy
  object PackStrategy {
    case class Limit(time: FiniteDuration)    extends PackStrategy
    case class Estimate(time: FiniteDuration) extends PackStrategy
    case object Unlimited                     extends PackStrategy
  }
}
