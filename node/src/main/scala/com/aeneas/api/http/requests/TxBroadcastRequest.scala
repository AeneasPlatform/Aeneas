package com.aeneas.api.http.requests

import com.aeneas.account.PublicKey
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Transaction
import com.aeneas.transaction.TxValidationError.GenericError

trait TxBroadcastRequest {
  def sender: Option[String]
  def senderPublicKey: Option[String]

  def toTxFrom(sender: PublicKey): Either[ValidationError, Transaction]

  def toTx: Either[ValidationError, Transaction] =
    for {
      sender <- senderPublicKey match {
        case Some(key) => PublicKey.fromBase58String(key)
        case None      => Left(GenericError("invalid.senderPublicKey"))
      }
      tx <- toTxFrom(sender)
    } yield tx
}
