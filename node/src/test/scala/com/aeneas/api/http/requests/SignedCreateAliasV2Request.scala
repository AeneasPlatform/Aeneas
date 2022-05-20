package com.aeneas.api.http.requests

import cats.implicits._
import com.aeneas.account.PublicKey
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.{CreateAliasTransaction, Proofs, Transaction}
import play.api.libs.json.{Format, Json}

case class SignedCreateAliasV2Request(
    senderPublicKey: String,
    fee: Long,
    alias: String,
    timestamp: Long,
    proofs: List[String]
) {
  def toTx: Either[ValidationError, CreateAliasTransaction] =
    for {
      _sender     <- PublicKey.fromBase58String(senderPublicKey)
      _proofBytes <- proofs.traverse(s => parseBase58(s, "invalid proof", Proofs.MaxProofStringSize))
      _proofs     <- Proofs.create(_proofBytes)
      _t          <- CreateAliasTransaction.create(Transaction.V1, _sender, alias, fee, timestamp, _proofs)
    } yield _t
}

object SignedCreateAliasV2Request {
  implicit val broadcastAliasV2RequestReadsFormat: Format[SignedCreateAliasV2Request] = Json.format
}
