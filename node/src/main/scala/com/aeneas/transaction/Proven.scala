package com.aeneas.transaction
import com.aeneas.common.state.ByteStr
import monix.eval.Coeval

trait Proven extends Authorized {
  def proofs: Proofs
  val bodyBytes: Coeval[Array[Byte]]
}

object Proven {
  implicit class ProvenExt(private val p: Proven) extends AnyVal {
    def signature: ByteStr = p.proofs.toSignature
  }
}
