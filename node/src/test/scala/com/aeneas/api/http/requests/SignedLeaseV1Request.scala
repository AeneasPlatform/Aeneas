package com.aeneas.api.http.requests

import com.aeneas.account.{AddressOrAlias, PublicKey}
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.lease.LeaseTransaction
import play.api.libs.json.{Format, Json}

case class SignedLeaseV1Request(
    senderPublicKey: String,
    amount: Long,
    fee: Long,
    recipient: String,
    timestamp: Long,
    signature: String
) {
  def toTx: Either[ValidationError, LeaseTransaction] =
    for {
      _sender    <- PublicKey.fromBase58String(senderPublicKey)
      _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
      _recipient <- AddressOrAlias.fromString(recipient)
      _t         <- LeaseTransaction.create(1.toByte, _sender, _recipient, amount, fee, timestamp, Proofs(_signature))
    } yield _t
}

object SignedLeaseV1Request {
  implicit val broadcastLeaseRequestReadsFormat: Format[SignedLeaseV1Request] = Json.format
}
