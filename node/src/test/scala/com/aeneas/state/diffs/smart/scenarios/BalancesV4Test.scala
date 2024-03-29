package com.aeneas.state.diffs.smart.scenarios

import cats.implicits._
import com.aeneas.account.Alias
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.db.WithState
import com.aeneas.features.BlockchainFeatures
import com.aeneas.lagonaki.mocks.TestBlock
import com.aeneas.lang.Global
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values.{Asset => AssetType, DApp => DAppType, _}
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.lang.script.{ContractScript, Script}
import com.aeneas.lang.v1.FunctionHeader.User
import com.aeneas.lang.v1.compiler.Terms._
import com.aeneas.lang.v1.compiler.{ContractCompiler, ExpressionCompiler}
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.aeneas.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.aeneas.lang.v1.parser._
import com.aeneas.lang.v1.traits.Environment
import com.aeneas.settings.{Constants, FunctionalitySettings, TestFunctionalitySettings}
import com.aeneas.state._
import com.aeneas.state.diffs._
import com.aeneas.transaction.Asset._
import com.aeneas.transaction._
import com.aeneas.transaction.assets._
import com.aeneas.transaction.lease._
import com.aeneas.transaction.smart._
import com.aeneas.transaction.transfer._
import com.aeneas.utils._
import com.aeneas.{NoShrink, TestTime, TransactionGen}
import org.scalatest.PropSpec
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class BalancesV4Test extends PropSpec with PropertyChecks with WithState with TransactionGen with NoShrink {

  val MinFee: Long            = Constants.UnitsInAsh / 1000L
  val DataTxFee: Long         = 15000000L
  val InvokeScriptTxFee: Long = 15000000L
  val MassTransferTxFee: Long = 15000000L
  val SetScriptFee: Long      = Constants.UnitsInAsh / 1000L
  val SetAssetScriptFee: Long = Constants.UnitsInAsh

  val rideV4Activated: FunctionalitySettings = TestFunctionalitySettings.Enabled.copy(
    preActivatedFeatures = Map(
      BlockchainFeatures.Ride4DApps.id    -> 0,
      BlockchainFeatures.SmartAccounts.id -> 0,
      BlockchainFeatures.BlockV5.id       -> 0
    )
  )
  val functionCall: Option[FUNCTION_CALL] =
    Some(
      FUNCTION_CALL(
        User("bar"),
        List()
      )
    )
  private val preconditionsAndTransfer = for {
    master <- accountGen
    acc1   <- accountGen
    dapp   <- accountGen
    ts     <- positiveIntGen
    genesis = Seq(
      GenesisTransaction.create(master.toAddress, ENOUGH_AMT, ts).explicitGet(),
      GenesisTransaction.create(acc1.toAddress, 25 * Constants.UnitsInAsh + 3 * MinFee, ts).explicitGet(),
      GenesisTransaction
        .create(dapp.toAddress, 10 * Constants.UnitsInAsh + SetScriptFee + 2 * InvokeScriptTxFee + 1 * Constants.UnitsInAsh, ts)
        .explicitGet(),
      CreateAliasTransaction.selfSigned(TxVersion.V2, acc1, Alias.create("alias").explicitGet(), MinFee, ts).explicitGet()
    )
    setScript = SetScriptTransaction.selfSigned(1.toByte, dapp, Some(script("alias")), SetScriptFee, ts).explicitGet()
    ci        = InvokeScriptTransaction.selfSigned(1.toByte, master, dapp.toAddress, functionCall, Nil, InvokeScriptTxFee, Waves, ts + 3).explicitGet()
    lease1    = LeaseTransaction.selfSigned(2.toByte, acc1, dapp.toAddress, 10 * Constants.UnitsInAsh, MinFee, ts + 2).explicitGet()
    lease2    = LeaseTransaction.selfSigned(2.toByte, acc1, dapp.toAddress, 10 * Constants.UnitsInAsh, MinFee, ts + 3).explicitGet()
    leaseD    = LeaseTransaction.selfSigned(2.toByte, dapp, acc1.toAddress, 1 * Constants.UnitsInAsh, MinFee, ts + 3).explicitGet()
    cancel1   = LeaseCancelTransaction.signed(1.toByte, acc1.publicKey, lease1.id(), MinFee, ts + 4, acc1.privateKey).explicitGet()
    t = TransferTransaction
      .selfSigned(TxVersion.V2, dapp, acc1.toAddress, Waves, 1 * Constants.UnitsInAsh + MinFee, Waves, InvokeScriptTxFee, ByteStr.empty, ts + 5)
      .explicitGet()
  } yield {
    (genesis ++ Seq(setScript, lease1, lease2), Seq(cancel1, leaseD, t), acc1, dapp, ci)
  }

  def script(a: String): Script = {
    val ctx = {
      val directives = DirectiveSet(V4, Account, DAppType).explicitGet()
      PureContext.build(Global, V4).withEnvironment[Environment] |+|
        CryptoContext.build(Global, V4).withEnvironment[Environment] |+|
        WavesContext.build(directives)
    }

    val script =
      s"""
         | {-#STDLIB_VERSION 4 #-}
         | {-#SCRIPT_TYPE ACCOUNT #-}
         | {-#CONTENT_TYPE DAPP #-}
         |
         | @Callable(i)
         | func bar() = {
         |  let balance = wavesBalance(Alias("$a"))
         |   [
         |     IntegerEntry("available", balance.available),
         |     IntegerEntry("regular", balance.regular),
         |     IntegerEntry("generating", balance.generating),
         |     IntegerEntry("effective", balance.effective)
         |   ]
         | }
      """.stripMargin
    val dApp = ContractCompiler.compile(script, ctx.compilerContext, V4).explicitGet()
    ContractScript(V4, dApp).explicitGet()
  }

  property("Waves balance details") {

    forAll(preconditionsAndTransfer) {
      case (genesis, b, acc1, dapp, ci) =>
        assertDiffAndState(
          Seq(TestBlock.create(genesis)) ++
            (0 to 1000).map(_ => TestBlock.create(Seq())) ++
            Seq(TestBlock.create(b)),
          TestBlock.create(Seq(ci)),
          rideV4Activated
        ) {
          case (d, s) =>
            val apiBalance = com.aeneas.api.common.CommonAccountsApi(d, db, s).balanceDetails(acc1.toAddress)
            val data       = d.accountData(dapp.toAddress)
            data.data("available") shouldBe IntegerDataEntry("available", apiBalance.available)
            apiBalance.available shouldBe 16 * Constants.UnitsInAsh
            data.data("regular") shouldBe IntegerDataEntry("regular", apiBalance.regular)
            apiBalance.regular shouldBe 26 * Constants.UnitsInAsh
            data.data("generating") shouldBe IntegerDataEntry("generating", apiBalance.generating)
            apiBalance.generating shouldBe 5 * Constants.UnitsInAsh
            data.data("effective") shouldBe IntegerDataEntry("effective", apiBalance.effective)
            apiBalance.effective shouldBe 17 * Constants.UnitsInAsh

        }
    }
  }

  property("Asset balance change while processing script result") {
    val a = 10000000000L
    def assetScript(acc: ByteStr): Script = {
      val ctx = {
        val directives = DirectiveSet(V4, AssetType, Expression).explicitGet()
        PureContext.build(Global, V4).withEnvironment[Environment] |+|
          CryptoContext.build(Global, V4).withEnvironment[Environment] |+|
          WavesContext.build(directives)
      }

      val script =
        s"""
          | {-# STDLIB_VERSION 4 #-}
          | {-# CONTENT_TYPE EXPRESSION #-}
          | {-# SCRIPT_TYPE ASSET #-}
          |
          | assetBalance(Address(base58'$acc'), this.id) == $a && assetBalance(Alias("alias"), this.id) == $a
        """.stripMargin
      val parsedScript = Parser.parseExpr(script).get.value
      ExprScript(V4, ExpressionCompiler(ctx.compilerContext, parsedScript).explicitGet()._1)
        .explicitGet()
    }

    def dappScript(acc: ByteStr, asset: ByteStr): Script = {
      val ctx = {
        val directives = DirectiveSet(V4, Account, DAppType).explicitGet()
        PureContext.build(Global, V4).withEnvironment[Environment] |+|
          CryptoContext.build(Global, V4).withEnvironment[Environment] |+|
          WavesContext.build(directives)
      }

      val script =
        s"""
           | {-#STDLIB_VERSION 4 #-}
           | {-#SCRIPT_TYPE ACCOUNT #-}
           | {-#CONTENT_TYPE DAPP #-}
           |
           | @Callable(i)
           | func bar() = {
           |   [
           |    ScriptTransfer(Address(base58'$acc'), 1, base58'$asset'),
           |    Reissue(base58'$asset', false, 2)
           |   ]
           | }
        """.stripMargin
      val dApp = ContractCompiler.compile(script, ctx.compilerContext, V4).explicitGet()
      ContractScript(V4, dApp).explicitGet()
    }

    val functionCall =
      Some(
        FUNCTION_CALL(
          User("bar"),
          List()
        )
      )

    val time         = new TestTime
    def nextTs: Long = time.getTimestamp()

    forAll(for { a <- accountGen; b <- accountGen } yield (a, b)) {
      case (acc1, acc2) =>
        val g1    = GenesisTransaction.create(acc1.toAddress, ENOUGH_AMT, nextTs).explicitGet()
        val g2    = GenesisTransaction.create(acc2.toAddress, ENOUGH_AMT, nextTs).explicitGet()
        val alias = CreateAliasTransaction.selfSigned(TxVersion.V2, acc2, Alias.create("alias").explicitGet(), MinFee, nextTs).explicitGet()
        val issue = IssueTransaction(
          TxVersion.V1,
          acc1.publicKey,
          "testsname".utf8Bytes,
          "testdesc".utf8Bytes,
          10000000000L,
          8.toByte,
          reissuable = true,
          script = Some(assetScript(ByteStr(acc1.toAddress.bytes))),
          SetAssetScriptFee,
          nextTs
        ).signWith(acc1.privateKey)
        val setScript = SetScriptTransaction
          .selfSigned(1.toByte, acc1, Some(dappScript(ByteStr(acc2.toAddress.bytes), issue.id())), SetScriptFee, nextTs)
          .explicitGet()
        val ci = InvokeScriptTransaction.selfSigned(1.toByte, acc1, acc1.toAddress, functionCall, Nil, InvokeScriptTxFee, Waves, nextTs).explicitGet()

        assertDiffAndState(Seq(TestBlock.create(Seq(g1, g2, alias, issue, setScript))), TestBlock.create(Seq(ci)), rideV4Activated) {
          case (d, s) =>
            val error = d.scriptResults(ci.id()).error
            error.get.code shouldBe 3
            error.get.text should include("Transaction is not allowed by script of the asset")
            s.balance(acc1.toAddress, IssuedAsset(issue.id())) shouldBe a
        }

    }
  }

  property("Waves balance change while processing script result") {
    val w = ENOUGH_AMT - SetScriptFee - SetAssetScriptFee
    def assetScript(acc: ByteStr): Script = {
      val ctx = {
        val directives = DirectiveSet(V4, AssetType, Expression).explicitGet()
        PureContext.build(Global, V4).withEnvironment[Environment] |+|
          CryptoContext.build(Global, V4).withEnvironment[Environment] |+|
          WavesContext.build(directives)
      }

      val script =
        s"""
          | {-# STDLIB_VERSION 4 #-}
          | {-# CONTENT_TYPE EXPRESSION #-}
          | {-# SCRIPT_TYPE ASSET #-}
          |
          | wavesBalance(Address(base58'$acc')).regular == $w
        """.stripMargin
      val parsedScript = Parser.parseExpr(script).get.value
      ExprScript(V4, ExpressionCompiler(ctx.compilerContext, parsedScript).explicitGet()._1)
        .explicitGet()
    }

    def dappScript(acc: ByteStr, asset: ByteStr): Script = {
      val ctx = {
        val directives = DirectiveSet(V4, Account, DAppType).explicitGet()
        PureContext.build(Global, V4).withEnvironment[Environment] |+|
          CryptoContext.build(Global, V4).withEnvironment[Environment] |+|
          WavesContext.build(directives)
      }

      val script =
        s"""
           | {-#STDLIB_VERSION 4 #-}
           | {-#SCRIPT_TYPE ACCOUNT #-}
           | {-#CONTENT_TYPE DAPP #-}
           |
           | @Callable(i)
           | func bar() = {
           |   [
           |    ScriptTransfer(Address(base58'$acc'), 1, unit),
           |    Reissue(base58'$asset', false, 1)
           |   ]
           | }
        """.stripMargin
      val dApp = ContractCompiler.compile(script, ctx.compilerContext, V4).explicitGet()
      ContractScript(V4, dApp).explicitGet()
    }

    val functionCall =
      Some(
        FUNCTION_CALL(
          User("bar"),
          List()
        )
      )

    val time         = new TestTime
    def nextTs: Long = time.getTimestamp()

    forAll(for { a <- accountGen; b <- accountGen } yield (a, b)) {
      case (acc1, acc2) =>
        val g1 = GenesisTransaction.create(acc1.toAddress, ENOUGH_AMT, nextTs).explicitGet()
        val g2 = GenesisTransaction.create(acc2.toAddress, ENOUGH_AMT, nextTs).explicitGet()
        val issue = IssueTransaction(
          TxVersion.V1,
          acc1.publicKey,
          "testsname".utf8Bytes,
          "testdesc".utf8Bytes,
          10000000000L,
          8.toByte,
          reissuable = true,
          script = Some(assetScript(ByteStr(acc1.toAddress.bytes))),
          SetAssetScriptFee,
          nextTs
        ).signWith(acc1.privateKey)
        val setScript = SetScriptTransaction
          .selfSigned(1.toByte, acc1, Some(dappScript(ByteStr(acc2.toAddress.bytes), issue.id())), SetScriptFee, nextTs)
          .explicitGet()
        val ci = InvokeScriptTransaction.selfSigned(1.toByte, acc2, acc1.toAddress, functionCall, Nil, InvokeScriptTxFee, Waves, nextTs).explicitGet()

        assertDiffAndState(Seq(TestBlock.create(Seq(g1, g2, issue, setScript))), TestBlock.create(Seq(ci)), rideV4Activated) {
          case (d, s) =>
            val error = d.scriptResults(ci.id()).error
            error.get.code shouldBe 3
            error.get.text should include("Transaction is not allowed by script of the asset")
            s.wavesPortfolio(acc1.toAddress).balance shouldBe w
        }

    }
  }

}
