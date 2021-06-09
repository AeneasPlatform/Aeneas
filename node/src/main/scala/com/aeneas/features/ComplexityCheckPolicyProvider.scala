package com.aeneas.features

import com.aeneas.state.Blockchain

object ComplexityCheckPolicyProvider {
  implicit class VerifierComplexityCheckExt(b: Blockchain) {
    val useReducedVerifierComplexityLimit: Boolean =
      b.activatedFeatures.contains(BlockchainFeatures.BlockV5.id)
  }
}
