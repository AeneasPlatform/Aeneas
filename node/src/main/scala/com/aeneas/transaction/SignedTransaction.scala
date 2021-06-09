package com.aeneas.transaction

import com.aeneas.common.state.ByteStr
import com.aeneas.crypto
import monix.eval.Coeval
import play.api.libs.json._

trait SignedTransaction extends ProvenTransaction with Signed {

  protected override def proofField: Seq[(String, JsValue)] = {
    val sig = JsString(this.signature.toString)
    Seq("signature" -> sig, "proofs" -> JsArray(Seq(sig)))
  }

  val signature: ByteStr

  def proofs: Proofs = Proofs(signature)

  val signatureValid: Coeval[Boolean] = Coeval.evalOnce(crypto.verify(signature, bodyBytes(), sender))
}
