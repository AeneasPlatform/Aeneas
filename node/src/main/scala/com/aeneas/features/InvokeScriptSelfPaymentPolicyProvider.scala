package com.aeneas.features

import com.aeneas.state.Blockchain

object InvokeScriptSelfPaymentPolicyProvider {
  implicit class SelfPaymentPolicyBlockchainExt(b: Blockchain) {
    val disallowSelfPayment: Boolean =
      b.isFeatureActivated(BlockchainFeatures.BlockV5)
  }
}
