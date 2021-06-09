package com.aeneas.database

import com.google.common.hash.{Funnels, BloomFilter => GBloomFilter}
import com.aeneas.account.Address
import com.aeneas.events.BlockchainUpdateTriggers
import com.aeneas.settings.WavesSettings
import com.aeneas.state.BlockchainUpdaterImpl
import com.aeneas.transaction.Asset
import com.aeneas.utils.Time
import monix.reactive.Observer
import org.iq80.leveldb.DB

object TestStorageFactory {
  private def wrappedFilter(): BloomFilter = new Wrapper(GBloomFilter.create(Funnels.byteArrayFunnel(), 1000L))

  def apply(
      settings: WavesSettings,
      db: DB,
      time: Time,
      spendableBalanceChanged: Observer[(Address, Asset)],
      blockchainUpdateTriggers: BlockchainUpdateTriggers
  ): (BlockchainUpdaterImpl, LevelDBWriter) = {
    val levelDBWriter: LevelDBWriter = new LevelDBWriter(db, spendableBalanceChanged, settings.blockchainSettings, settings.dbSettings) {
      override val orderFilter: BloomFilter        = wrappedFilter()
      override val dataKeyFilter: BloomFilter      = wrappedFilter()
      override val wavesBalanceFilter: BloomFilter = wrappedFilter()
      override val assetBalanceFilter: BloomFilter = wrappedFilter()
    }
    (
      new BlockchainUpdaterImpl(levelDBWriter, spendableBalanceChanged, settings, time, blockchainUpdateTriggers, loadActiveLeases(db, _, _)),
      levelDBWriter
    )
  }
}
