package com.aeneas.it.sync.smartcontract

import com.aeneas.account.{AddressScheme, Alias}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync.{minFee, setScriptFee, smartFee}
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.lang.v1.FunctionHeader
import com.aeneas.lang.v1.compiler.Terms
import com.aeneas.lang.v1.estimator.v2.ScriptEstimatorV2
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.smart.SetScriptTransaction
import com.aeneas.transaction.smart.script.ScriptCompiler
import com.aeneas.transaction.transfer.TransferTransaction
import com.aeneas.transaction.{CreateAliasTransaction, Transaction}
import org.scalatest.CancelAfterFailure

class ScriptExecutionErrorSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val acc0 = pkByAddress(firstAddress)
  private val acc1 = pkByAddress(secondAddress)
  private val acc2 = pkByAddress(thirdAddress)
  private val ts   = System.currentTimeMillis()

  test("custom throw message") {
    val scriptSrc =
      """
        |match tx {
        |  case t : TransferTransaction =>
        |    let res = if isDefined(t.assetId) then extract(t.assetId) == base58'' else isDefined(t.assetId) == false
        |    res
        |  case _: SetScriptTransaction => true
        |  case _ => throw("Your transaction has incorrect type.")
        |}
      """.stripMargin

    val compiled = ScriptCompiler(scriptSrc, isAssetScript = false, ScriptEstimatorV2).explicitGet()._1

    val tx = sender.signedBroadcast(SetScriptTransaction.selfSigned(1.toByte, acc2, Some(compiled), setScriptFee, ts).explicitGet().json())
    nodes.waitForHeightAriseAndTxPresent(tx.id)

    val alias = Alias.fromString(s"alias:${AddressScheme.current.chainId.toChar}:asdasdasdv").explicitGet()
    assertBadRequestAndResponse(
      sender.signedBroadcast(CreateAliasTransaction.selfSigned(Transaction.V2, acc2, alias, minFee + smartFee, ts).explicitGet().json()),
      "Your transaction has incorrect type."
    )
  }

  test("wrong type of script return value") {
    val script = ExprScript(
      Terms.FUNCTION_CALL(
        FunctionHeader.Native(100),
        List(Terms.CONST_LONG(3), Terms.CONST_LONG(2))
      )
    ).explicitGet()

    val tx = sender.signAndBroadcast(
      SetScriptTransaction
        .selfSigned(1.toByte, acc0, Some(script), setScriptFee, ts)
        .explicitGet()
        .json()
    )
    nodes.waitForHeightAriseAndTxPresent(tx.id)

    assertBadRequestAndResponse(
      sender.signedBroadcast(
        TransferTransaction.selfSigned(2.toByte, acc0, acc1.toAddress, Waves, 1000, Waves, minFee + smartFee, ByteStr.empty,  ts)
          .explicitGet()
          .json()
      ),
      "not a boolean"
    )
  }
}
