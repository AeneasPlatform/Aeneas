package com.aeneas.account

import com.aeneas.{EitherMatchers, NoShrink, TransactionGen}
import org.scalatest.{EitherValues, Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class AliasSpecification extends PropSpec with PropertyChecks with Matchers with EitherMatchers with TransactionGen with NoShrink with EitherValues {

  property("Correct alias should be valid") {
    forAll(validAliasStringGen) { s =>
      Alias.create(s) should beRight
    }
  }

  property("Incorrect alias should be invalid") {
    forAll(invalidAliasStringGen) { s =>
      Alias.create(s) should beLeft
    }
  }
}
