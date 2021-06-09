package com.aeneas.utils

import com.typesafe.config.ConfigFactory
import com.aeneas.account.{Address, Alias}
import com.aeneas.block.SignedBlockHeader
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.settings.BlockchainSettings
import com.aeneas.state._
import com.aeneas.state.reader.LeaseDetails
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.aeneas.transaction.TxValidationError.GenericError
import com.aeneas.transaction.transfer.TransferTransaction
import com.aeneas.transaction.{Asset, Transaction}

case object EmptyBlockchain extends Blockchain {
  override lazy val settings: BlockchainSettings = BlockchainSettings.fromRootConfig(ConfigFactory.load())

  override def height: Int = 0

  override def score: BigInt = 0

  override def blockHeader(height: Int): Option[SignedBlockHeader] = None

  override def hitSource(height: Int): Option[ByteStr] = None

  override def carryFee: Long = 0

  override def heightOf(blockId: ByteStr): Option[Int] = None

  /** Features related */
  override def approvedFeatures: Map[Short, Int] = Map.empty

  override def activatedFeatures: Map[Short, Int] = Map.empty

  override def featureVotes(height: Int): Map[Short, Int] = Map.empty

  /** Block reward related */
  override def blockReward(height: Int): Option[Long] = None

  override def blockRewardVotes(height: Int): Seq[Long] = Seq.empty

  override def wavesAmount(height: Int): BigInt = 0

  override def transferById(id: ByteStr): Option[(Int, TransferTransaction)] = None

  override def transactionInfo(id: ByteStr): Option[(Int, Transaction, Boolean)] = None

  override def transactionHeight(id: ByteStr): Option[Int] = None

  override def containsTransaction(tx: Transaction): Boolean = false

  override def assetDescription(id: IssuedAsset): Option[AssetDescription] = None

  override def resolveAlias(a: Alias): Either[ValidationError, Address] = Left(GenericError("Empty blockchain"))

  override def leaseDetails(leaseId: ByteStr): Option[LeaseDetails] = None

  override def filledVolumeAndFee(orderId: ByteStr): VolumeAndFee = VolumeAndFee(0, 0)

  /** Retrieves Waves balance snapshot in the [from, to] range (inclusive) */
  override def balanceAtHeight(address: Address, height: Int, assetId: Asset = Waves): Option[(Int, Long)] = Option.empty
  override def balanceSnapshots(address: Address, from: Int, to: Option[ByteStr]): Seq[BalanceSnapshot]         = Seq.empty

  override def accountScript(address: Address): Option[AccountScriptInfo] = None

  override def hasAccountScript(address: Address): Boolean = false

  override def assetScript(asset: IssuedAsset): Option[AssetScriptInfo] = None

  override def accountData(acc: Address, key: String): Option[DataEntry[_]] = None

  override def balance(address: Address, mayBeAssetId: Asset): Long = 0

  override def leaseBalance(address: Address): LeaseBalance = LeaseBalance.empty
}
