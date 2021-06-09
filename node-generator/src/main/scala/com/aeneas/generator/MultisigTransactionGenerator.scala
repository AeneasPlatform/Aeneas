package com.aeneas.generator

import cats.Show
import com.aeneas.account.KeyPair
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.crypto
import com.aeneas.generator.utils.Gen
import com.aeneas.generator.utils.Implicits.DoubleExt
import com.aeneas.lang.script.Script
import com.aeneas.lang.v1.estimator.ScriptEstimator
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.smart.SetScriptTransaction
import com.aeneas.transaction.transfer.TransferTransaction
import com.aeneas.transaction.{Proofs, Transaction}

import scala.util.Random

class MultisigTransactionGenerator(settings: MultisigTransactionGenerator.Settings, val accounts: Seq[KeyPair], estimator: ScriptEstimator)
    extends TransactionGenerator {

  override def next(): Iterator[Transaction] = generate(settings).iterator

  private def generate(settings: MultisigTransactionGenerator.Settings): Seq[Transaction] = {

    val bank   = accounts.head
    val owners = Seq(createAccount(), accounts(1), createAccount(), accounts(2), createAccount(), accounts(3), createAccount(), createAccount())

    val enoughFee               = 0.005.waves
    val totalAmountOnNewAccount = 1.waves

    val script: Script = Gen.multiSigScript(owners, 3, estimator)

    val now       = System.currentTimeMillis()
    val setScript = SetScriptTransaction.selfSigned(1.toByte, bank, Some(script), enoughFee, now).explicitGet()

    val res = Range(0, settings.transactions).map { i =>
      val tx = TransferTransaction(
        2.toByte,
        bank.publicKey,
        owners(1).toAddress,
        Waves,
        totalAmountOnNewAccount - 2 * enoughFee - i,
        Waves,
        enoughFee,
        ByteStr.empty,
        now + i,
        Proofs.empty,
        owners(1).toAddress.chainId
      )
      val signatures = owners.map(o => crypto.sign(o.privateKey, tx.bodyBytes()))
      tx.copy(proofs = Proofs(signatures))
    }

    println(System.currentTimeMillis())
    println(s"${res.length} tx generated")

    if (settings.firstRun) setScript +: res
    else res
  }

  private def createAccount() = {
    val seedBytes = Array.fill(32)(0: Byte)
    Random.nextBytes(seedBytes)
    KeyPair(seedBytes)
  }
}

object MultisigTransactionGenerator {
  final case class Settings(transactions: Int, firstRun: Boolean)

  object Settings {
    implicit val toPrintable: Show[Settings] = { x =>
      s"""
        | transactions = ${x.transactions}
        | firstRun = ${x.firstRun}
      """.stripMargin
    }
  }
}
