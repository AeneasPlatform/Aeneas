package com.aeneas.transaction

import com.aeneas.account.Address

case class AssetAcc(account: Address, assetId: Option[Asset])
