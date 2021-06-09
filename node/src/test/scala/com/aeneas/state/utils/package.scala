package com.aeneas.state

import com.aeneas.account.Address
import com.aeneas.api.common.AddressTransactions
import com.aeneas.common.state.ByteStr
import com.aeneas.database.{DBResource, LevelDBWriter, TestStorageFactory}
import com.aeneas.events.BlockchainUpdateTriggers
import com.aeneas.settings.TestSettings._
import com.aeneas.settings.{BlockchainSettings, FunctionalitySettings, GenesisSettings, RewardsSettings, TestSettings}
import com.aeneas.transaction.{Asset, Transaction}
import com.aeneas.utils.SystemTime
import monix.reactive.Observer
import org.iq80.leveldb.DB

package object utils {

  def addressTransactions(
      db: DB,
      diff: => Option[(Height, Diff)],
      address: Address,
      types: Set[Transaction.Type],
      fromId: Option[ByteStr]
  ): Seq[(Height, Transaction)] = {
    val resource = DBResource(db)
    try AddressTransactions.allAddressTransactions(resource, diff, address, None, types, fromId).map { case (h, tx, _) => h -> tx }.toSeq
    finally resource.close()
  }

  object TestLevelDB {
    def withFunctionalitySettings(
        writableDB: DB,
        spendableBalanceChanged: Observer[(Address, Asset)],
        fs: FunctionalitySettings
    ): LevelDBWriter =
      TestStorageFactory(
        TestSettings.Default.withFunctionalitySettings(fs),
        writableDB,
        SystemTime,
        spendableBalanceChanged,
        BlockchainUpdateTriggers.noop
      )._2

    def createTestBlockchainSettings(fs: FunctionalitySettings): BlockchainSettings =
      BlockchainSettings('T', fs, GenesisSettings.TESTNET, RewardsSettings.TESTNET)
  }
}
