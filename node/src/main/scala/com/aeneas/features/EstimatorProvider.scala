package com.aeneas.features

import com.aeneas.features.BlockchainFeatures.{BlockReward, BlockV5}
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.lang.v1.estimator.v3.ScriptEstimatorV3
import com.aeneas.lang.v1.estimator.{ScriptEstimator, ScriptEstimatorV1}
import com.aeneas.settings.WavesSettings
import com.aeneas.state.Blockchain

object EstimatorProvider {
  implicit class EstimatorBlockchainExt(b: Blockchain) {
    val estimator: ScriptEstimator =
      if (b.isFeatureActivated(BlockV5)) ScriptEstimatorV3
      else if (b.isFeatureActivated(BlockReward)) ScriptEstimatorV2
      else ScriptEstimatorV1
  }

  implicit class EstimatorWavesSettingsExt(ws: WavesSettings) {
    val estimator: ScriptEstimator =
      if (ws.featuresSettings.supported.contains(BlockV5.id)) ScriptEstimatorV3
      else if (ws.featuresSettings.supported.contains(BlockReward.id)) ScriptEstimatorV2
      else ScriptEstimatorV1
  }
}
