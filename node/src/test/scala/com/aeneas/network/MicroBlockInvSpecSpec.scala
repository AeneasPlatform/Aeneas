package com.aeneas.network

import com.aeneas.common.state.ByteStr
import com.aeneas.crypto._
import com.aeneas.{EitherMatchers, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.concurrent.Eventually
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class MicroBlockInvSpecSpec extends FreeSpec with Matchers with EitherMatchers with PropertyChecks with Eventually with TransactionGen {

  private val microBlockInvGen: Gen[MicroBlockInv] = for {
    acc          <- accountGen
    totalSig     <- byteArrayGen(SignatureLength)
    prevBlockSig <- byteArrayGen(SignatureLength)
  } yield MicroBlockInv(acc, ByteStr(totalSig), ByteStr(prevBlockSig))

  "MicroBlockInvMessageSpec" - {
    import MicroBlockInvSpec._

    "deserializeData(serializedData(data)) == data" in forAll(microBlockInvGen) { inv =>
      inv.signaturesValid() should beRight
      val restoredInv = deserializeData(serializeData(inv)).get
      restoredInv.signaturesValid() should beRight

      restoredInv shouldBe inv
    }
  }

}
