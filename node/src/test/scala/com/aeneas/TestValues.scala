package com.aeneas

import com.aeneas.account.{Address, KeyPair}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.lang.v1.estimator.ScriptEstimatorV1
import com.aeneas.transaction.Asset.IssuedAsset
//import com.aeneas.transaction.TxHelpers
import com.aeneas.transaction.smart.script.ScriptCompiler

object TestValues {
  val OneWaves: Long      = 1e8.toLong
  val ThousandWaves: Long = OneWaves * 1000

  val keyPair: KeyPair   = ??? //TxHelpers.defaultSigner
  val address: Address   = keyPair.toAddress
  val asset: IssuedAsset = IssuedAsset(ByteStr(("A" * 32).getBytes("ASCII")))
  val bigMoney: Long     = com.aeneas.state.diffs.ENOUGH_AMT
  val timestamp: Long    = System.currentTimeMillis()
  val fee: Long          = 1e6.toLong

  val (script, scriptComplexity) = ScriptCompiler
    .compile(
      """
      |{-# STDLIB_VERSION 2 #-}
      |{-# CONTENT_TYPE EXPRESSION #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |true
      |""".stripMargin,
      ScriptEstimatorV1
    )
    .explicitGet()

  val (assetScript, assetScriptComplexity) = ScriptCompiler
    .compile(
      """
      |{-# STDLIB_VERSION 2 #-}
      |{-# CONTENT_TYPE EXPRESSION #-}
      |{-# SCRIPT_TYPE ASSET #-}
      |true
      |""".stripMargin,
      ScriptEstimatorV1
    )
    .explicitGet()

  val (rejectAssetScript, rejectAssetScriptComplexity) = ScriptCompiler
    .compile(
      """
      |{-# STDLIB_VERSION 2 #-}
      |{-# CONTENT_TYPE EXPRESSION #-}
      |{-# SCRIPT_TYPE ASSET #-}
      |false
      |""".stripMargin,
      ScriptEstimatorV1
    )
    .explicitGet()
}
