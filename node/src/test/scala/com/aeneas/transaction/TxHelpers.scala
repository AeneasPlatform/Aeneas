package com.aeneas.transaction

import com.google.common.primitives.Ints
import com.aeneas.TestValues
import com.aeneas.account.{Address, AddressOrAlias, KeyPair}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils._
import com.aeneas.lang.script.Script
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.assets.exchange.{AssetPair, ExchangeTransaction, Order, OrderType}
import com.aeneas.transaction.transfer.TransferTransaction

object TxHelpers {
  def signer(i: Int): KeyPair = KeyPair(Ints.toByteArray(i))
  val defaultSigner: KeyPair  = signer(0)

  private[this] var lastTimestamp = System.currentTimeMillis()
  def timestamp: Long = {
    lastTimestamp += 1
    lastTimestamp
  }

  def genesis(address: Address, amount: Long = TestValues.OneWaves * 1000): GenesisTransaction =
    GenesisTransaction.create(address, amount, timestamp).explicitGet()

  def transfer(from: KeyPair, to: AddressOrAlias, amount: Long = TestValues.OneWaves, asset: Asset = Waves): TransferTransaction =
    TransferTransaction.selfSigned(TxVersion.V1, from, to, asset, amount, Waves, TestValues.fee, ByteStr.empty, timestamp).explicitGet()

  def issue(amount: Long = TestValues.ThousandWaves, script: Script = null): IssueTransaction =
    IssueTransaction
      .selfSigned(TxVersion.V2, defaultSigner, "test", "", amount, 0, reissuable = false, Option(script), TestValues.OneWaves, timestamp)
      .explicitGet()

  def orderV3(orderType: OrderType, asset: Asset, feeAsset: Asset): Order = {
    orderV3(orderType, asset, Waves, feeAsset)
  }

  def orderV3(orderType: OrderType, amountAsset: Asset, priceAsset: Asset, feeAsset: Asset): Order = {
    Order.selfSigned(
      TxVersion.V3,
      defaultSigner,
      defaultSigner.publicKey,
      AssetPair(amountAsset, priceAsset),
      orderType,
      1,
      1,
      timestamp,
      timestamp + 100000,
      1,
      feeAsset
    )
  }

  def exchange(order1: Order, order2: Order): ExchangeTransaction = {
    ExchangeTransaction
      .signed(
        TxVersion.V2,
        defaultSigner.privateKey,
        order1,
        order2,
        order1.amount,
        order1.price,
        order1.matcherFee,
        order2.matcherFee,
        TestValues.fee,
        timestamp
      )
      .explicitGet()
  }
}
