package com.aeneas.state.diffs

import com.aeneas.features.BlockchainFeatures
import com.aeneas.settings.{FunctionalitySettings, TestFunctionalitySettings}

package object smart {
  val smartEnabledFS: FunctionalitySettings =
    TestFunctionalitySettings.Enabled.copy(
      preActivatedFeatures = Map(
        BlockchainFeatures.SmartAccounts.id   -> 0,
        BlockchainFeatures.SmartAssets.id     -> 0,
        BlockchainFeatures.DataTransaction.id -> 0,
        BlockchainFeatures.Ride4DApps.id      -> 0
      )
    )
}
