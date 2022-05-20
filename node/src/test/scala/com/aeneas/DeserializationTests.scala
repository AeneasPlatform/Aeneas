package com.aeneas

import com.aeneas.serialization.Deser
import org.scalatest.{FreeSpec, Matchers}

class DeserializationTests extends FreeSpec with Matchers {

  "serializeArray" - {
    "works with arrays < 32k" in {
      val byteArray = Array.fill(Short.MaxValue)(0.toByte)
      Deser.serializeArrayWithLength(byteArray) should not be empty
    }
    "IllegalArgumentException thrown with arrays > 32k" in {
      val byteArray = Array.fill(Short.MaxValue + 1)(0.toByte)
      an[IllegalArgumentException] should be thrownBy Deser.serializeArrayWithLength(byteArray)
    }
  }
}
