package com.aeneas.events

import com.aeneas.account.Address
import com.aeneas.block.{Block, MicroBlock}
import com.aeneas.common.state.ByteStr
import com.aeneas.state.{AssetScriptInfo, DataEntry, LeaseBalance}
import com.aeneas.transaction.Asset
import com.aeneas.transaction.Asset.IssuedAsset

final case class AssetStateUpdate(
    asset: IssuedAsset,
    decimals: Int,
    name: ByteStr,
    description: ByteStr,
    reissuable: Boolean,
    volume: BigInt,
    script: Option[AssetScriptInfo],
    sponsorship: Option[Long],
    nft: Boolean,
    assetExistedBefore: Boolean
)

final case class StateUpdate(
    balances: Seq[(Address, Asset, Long)],
    leases: Seq[(Address, LeaseBalance)],
    dataEntries: Seq[(Address, DataEntry[_])],
    assets: Seq[AssetStateUpdate]
) {
  def isEmpty: Boolean = balances.isEmpty && leases.isEmpty && dataEntries.isEmpty && assets.isEmpty
}

sealed trait BlockchainUpdated extends Product with Serializable {
  def toId: ByteStr
  def toHeight: Int
}
final case class BlockAppended(
    toId: ByteStr,
    toHeight: Int,
    block: Block,
    updatedWavesAmount: Long,
    blockStateUpdate: StateUpdate,
    transactionStateUpdates: Seq[StateUpdate]
) extends BlockchainUpdated
final case class MicroBlockAppended(
    toId: ByteStr,
    toHeight: Int,
    microBlock: MicroBlock,
    microBlockStateUpdate: StateUpdate,
    transactionStateUpdates: Seq[StateUpdate]
) extends BlockchainUpdated
final case class RollbackCompleted(toId: ByteStr, toHeight: Int)           extends BlockchainUpdated
final case class MicroBlockRollbackCompleted(toId: ByteStr, toHeight: Int) extends BlockchainUpdated
