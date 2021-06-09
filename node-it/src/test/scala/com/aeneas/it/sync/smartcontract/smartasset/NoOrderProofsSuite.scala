package com.aeneas.it.sync.smartcontract.smartasset

import com.aeneas.account.AddressScheme
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync.{someAssetAmount, _}
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.assets.BurnTransaction
import com.aeneas.transaction.smart.script.ScriptCompiler
import com.aeneas.transaction.transfer.TransferTransaction

import scala.concurrent.duration._

class NoOrderProofsSuite extends BaseTransactionSuite {
  val estimator = ScriptEstimatorV2
  test("try to use Order in asset scripts") {
    try {
      sender.issue(
        firstAddress,
        "assetWProofs",
        "Test coin for assetWProofs test",
        someAssetAmount,
        0,
        reissuable = true,
        issueFee,
        2: Byte,
        script = Some(
          ScriptCompiler(
            s"""
              |match tx {
              |  case _: Order => true
              |  case _ => false
              |}""".stripMargin,
            isAssetScript = true,
            estimator
          ).explicitGet()._1.bytes.value.base64
        )
      )

      fail("ScriptCompiler didn't throw expected error")
    } catch {
      case ex: java.lang.Exception => ex.getMessage should include("Compilation failed: [Matching not exhaustive")
      case _: Throwable            => fail("ScriptCompiler works incorrect for orders with smart assets")
    }
  }

  test("try to use proofs in assets script") {
    val errProofMsg = "Reason: Proof doesn't validate as signature"
    val assetWProofs = sender
      .issue(
        firstAddress,
        "assetWProofs",
        "Test coin for assetWProofs test",
        someAssetAmount,
        0,
        reissuable = true,
        issueFee,
        2: Byte,
        script = Some(
          ScriptCompiler(
            s"""
                let proof = base58'assetWProofs'
                match tx {
                  case _: SetAssetScriptTransaction | TransferTransaction | ReissueTransaction | BurnTransaction => tx.proofs[0] == proof
                  case _ => false
                }""".stripMargin,
            false,
            estimator
          ).explicitGet()._1.bytes.value.base64
        ),
        waitForTx = true
      )
      .id

    val incorrectTrTx = TransferTransaction(
      2.toByte,
      pkByAddress(firstAddress).publicKey,
      pkByAddress(thirdAddress).toAddress,
      IssuedAsset(ByteStr.decodeBase58(assetWProofs).get),
      1,
      Waves,
      smartMinFee, ByteStr.empty,
      System.currentTimeMillis + 10.minutes.toMillis,
      Proofs(Seq(ByteStr("assetWProofs".getBytes("UTF-8")))),
      AddressScheme.current.chainId
    )

    assertBadRequestAndMessage(
      sender.signedBroadcast(incorrectTrTx.json()),
      errProofMsg
    )

    val incorrectBrTx = BurnTransaction
      .create(
        2.toByte,
        pkByAddress(firstAddress).publicKey,
        IssuedAsset(ByteStr.decodeBase58(assetWProofs).get),
        1,
        smartMinFee,
        System.currentTimeMillis + 10.minutes.toMillis,
        Proofs(Seq(ByteStr("assetWProofs".getBytes("UTF-8")))),
        AddressScheme.current.chainId
      )
      .explicitGet()

    assertBadRequestAndMessage(
      sender.signedBroadcast(incorrectBrTx.json()),
      errProofMsg
    )
  }

}
