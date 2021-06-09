package com.aeneas.extensions

import akka.actor.ActorSystem
import com.aeneas.account.Address
import com.aeneas.api.common._
import com.aeneas.common.state.ByteStr
import com.aeneas.events.{BlockchainUpdated, UtxEvent}
import com.aeneas.lang.ValidationError
import com.aeneas.settings.WavesSettings
import com.aeneas.state.Blockchain
import com.aeneas.transaction.smart.script.trace.TracedResult
import com.aeneas.transaction.{Asset, DiscardedBlocks, Transaction}
import com.aeneas.utils.Time
import com.aeneas.utx.UtxPool
import com.aeneas.wallet.Wallet
import monix.eval.Task
import monix.reactive.Observable

trait Context {
  def settings: WavesSettings
  def blockchain: Blockchain
  def rollbackTo(blockId: ByteStr): Task[Either[ValidationError, DiscardedBlocks]]
  def time: Time
  def wallet: Wallet
  def utx: UtxPool

  def transactionsApi: CommonTransactionsApi
  def blocksApi: CommonBlocksApi
  def accountsApi: CommonAccountsApi
  def assetsApi: CommonAssetsApi

  def broadcastTransaction(tx: Transaction): TracedResult[ValidationError, Boolean]
  def spendableBalanceChanged: Observable[(Address, Asset)]
  def blockchainUpdated: Observable[BlockchainUpdated]
  def utxEvents: Observable[UtxEvent]
  def actorSystem: ActorSystem
}
