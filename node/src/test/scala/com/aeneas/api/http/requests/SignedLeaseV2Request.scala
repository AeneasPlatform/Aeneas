package com.aeneas.api.http.requests

import cats.implicits._
import com.aeneas.account.{AddressOrAlias, PublicKey}
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.lease.LeaseTransaction
import play.api.libs.json.{Format, Json}

case class SignedLeaseV2Request(senderPublicKey: String, amount: Long, fee: Long, recipient: String, timestamp: Long, proofs: List[String]) {
  def toTx: Either[ValidationError, LeaseTransaction] =
    for {
      _sender     <- PublicKey.fromBase58String(senderPublicKey)
      _proofBytes <- proofs.traverse(s => parseBase58(s, "invalid proof", Proofs.MaxProofStringSize))
      _proofs     <- Proofs.create(_proofBytes)
      _recipient  <- AddressOrAlias.fromString(recipient)
      _t          <- LeaseTransaction.create(2.toByte, _sender, _recipient, amount, fee, timestamp, _proofs)
    } yield _t
}

object SignedLeaseV2Request {
  implicit val broadcastLeaseRequestReadsFormat: Format[SignedLeaseV2Request] = Json.format
}
