package com.aeneas.account

import com.aeneas.EitherMatchers
import com.aeneas.common.utils.EitherExt2
import org.scalatest.{Inside, Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class AccountOrAliasTests extends PropSpec with PropertyChecks with Matchers with EitherMatchers with Inside {

  property("Account should get parsed correctly") {
    AddressOrAlias.fromString("ÆxGDfUU6AdEMnbBEtHrp5qd2bsX8i9A6HhbGsW6zXR4Je5DUJBCTH").explicitGet() shouldBe an[Address]
    AddressOrAlias.fromString("4dfqcEt9RrPqGyTfh5cV3Y1Pr4ynwfS3L98jPRKQkeswefWh7n").explicitGet() shouldBe an[Address]
//    AddressOrAlias.fromString("Æx4dfqcEt9RrPqGyTfh5cV3Y1Pr4ynwfS3L98jPRKQkeswefWh7n").explicitGet() shouldBe an[Address]
//    AddressOrAlias.fromString("Æx3h9vwQ9nguTwqQgjwVT4BitLCHrmiF29dDuLCxNjcpaBmpANeZ").explicitGet() shouldBe an[Address]
  }

  property("Alias should get parsed correctly") {
    inside(AddressOrAlias.fromString("alias:T:sasha").explicitGet()) {
      case alias: Alias =>
        alias.name shouldBe "sasha"
        alias.chainId shouldBe 'T'
    }

    val alias2 = Alias.fromString("alias:T:sasha").explicitGet()
    alias2.name shouldBe "sasha"
    alias2.chainId shouldBe 'T'
  }

  property("Alias can be from other network") {
    AddressOrAlias.fromString("alias:Q:sasha") shouldBe Alias.createWithChainId("sasha", 'Q'.toByte)
  }

  property("Malformed aliases cannot be reconstructed") {
    AddressOrAlias.fromString("alias::sasha") should beLeft
    AddressOrAlias.fromString("alias:T: sasha") should beLeft
    AddressOrAlias.fromString("alias:T:sasha\nivanov") should beLeft
    AddressOrAlias.fromString("alias:T:s") should beLeft
    AddressOrAlias.fromString("alias:TTT:sasha") should beLeft

    Alias.fromString("alias:T: sasha") should beLeft
    Alias.fromString("alias:T:sasha\nivanov") should beLeft
    Alias.fromString("alias::sasha") should beLeft
    Alias.fromString("alias:T:s") should beLeft
    Alias.fromString("alias:TTT:sasha") should beLeft

    Alias.fromString("aliaaas:W:sasha") should beLeft
  }

  property("Unknown address schemes cannot be parsed") {
    AddressOrAlias.fromString("postcode:119072") should beLeft
  }
}
