package com.aeneas.transaction

import com.aeneas.{TransactionGen, crypto}
import org.scalacheck.Gen
import org.scalatest._
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}
import play.api.libs.json._

abstract class GenericTransactionSpecification[T <: com.aeneas.transaction.Transaction]
    extends PropSpec
    with PropertyChecks
    with Matchers
    with TransactionGen {

  def transactionParser: com.aeneas.transaction.TransactionParser
  def updateProofs(tx: T, p: Proofs): T
  def generator: Gen[(Seq[com.aeneas.transaction.Transaction], T)]
  def assertTxs(first: Transaction, second: T): Unit
  def jsonRepr: Seq[(JsValue, T)]
  def transactionName: String
  def preserBytesJson: Option[(Array[Byte], JsValue)] = None

  property(s"$transactionName serialization roundtrip") {
    forAll(generator) {
      case (_, tx) =>
        assertTxs(transactionParser.parseBytes(tx.bytes()).get, tx)
    }
  }

  preserBytesJson.foreach {
    case (bytes, json) =>
      property(s"$transactionName deserialize bytes") {
        val tx = transactionParser.parseBytes(bytes).get
        tx.json() shouldBe json
        tx match {
          case tx: ProvenTransaction =>
            assert(crypto.verify(tx.signature, tx.bodyBytes(), tx.sender), "signature should be valid")

          case _ => // Ignore
        }
      }
  }

  property(s"$transactionName serialization from TypedTransaction") {
    forAll(generator) { t =>
      val tx        = t._2
      val recovered = TransactionParsers.parseBytes(tx.bytes()).get
      assertTxs(recovered.asInstanceOf[T], tx)
    }
  }

  property(s"$transactionName id doesn't depend on proof") {
    forAll(generator, proofsGen) {
      case ((pref, tx), proofs1) =>
        val tx1 = updateProofs(tx, proofs1)
        tx1.id() shouldBe tx.id()
    }
  }

  property(s"$transactionName JSON format validation") {
    for ((js, tx) <- jsonRepr) {
      js shouldEqual tx.json()
    }
  }
}
