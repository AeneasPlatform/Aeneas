package com.aeneas.account

import com.aeneas.EitherMatchers
import com.aeneas.common.utils.{Base58, Base64, EitherExt2}
import com.aeneas.wallet.Wallet
import org.scalatest.{Inside, Matchers, PropSpec}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

import java.nio.charset.StandardCharsets

/**
 * @author luger. Created on 7/9/20.
 * @version
 */
class AddressTest extends PropSpec with PropertyChecks with Matchers with EitherMatchers with Inside {

    property("Account should get parsed correctly") {
      AddressOrAlias.fromString("Æx4dfqcEt9RrPqGyTfh5cV3Y1Pr4ynwfS3L98jPRKQkeswefWh7n").explicitGet() shouldBe an[Address]
    }

    property("Check seed and addr") {

      val originalSeed = """liguloid dermobranchia xylographer improvidence qasidas intentionalism freedwoman vernissage haden khellin xanthotic gang smooth-sided woodruffs hemeralope limousin pentachromic solutionis zygomycete tilburies electrotonic hoskinston all-affecting attainders"""

      val base58Str = "2hX532PN7TBGJoTYPDjUQpFHKF1CVw8cvrypE1kZtWVNiRVzbJDVNp8GW7FYRu79Pebfq4VwRG2qCPZ54dxkLdmPV7uKGC2BkWMk53Q2TrnaykvxGthtD4zJU6r67frAX25DF4nm4xZr44ST11aNtGMHAFt4mEq8hJFPcLpRaJeJSZnEFvDQAC3gL39qgT9A9MQjT7vadMNSb3WbNeXcpvNHs8Y7D9WDhFWgARGUpbKr6KTSHcJCg2CZTQVS73Pfz9BaiciHXePJGRGuyBAbyFWJh4cNQJsKXyjrK6Hk3RtsybRf64eEfDPzw23E"

      val base58str2 = "2An1jA5exLrDTMjdfNb7diBPKjpeYXLJfpX7aa3Sf227TQGYbut2y9mibu6FbkCXkXBkY6g6snz9bsrQH4HcbE48JQiTy6fMZgT75VxgrgMzGmhWLR5fpWFdHs7WKjYMyTyJhaf7FFrqeUJt3QvadHcXhwN4w1wffVjnQwJ2uWqmNHJzGWsWUVEA8HSdE9h8HpttXR3HSc3eMuhD4sV7CgVxBbwVMAKb9Bpn63S3C2obdiaRoVLAwbbymQAEd9K5r5H7jtV9EUyMR3ceTnrSDLtVvyd5UGLBCG6Wk1bczAvVfqxvnY8gxQeJCZ2"
      val decoded = new String(Base58.decode(base58Str), "UTF-8")
      println(s"decoded $decoded")
      //val a = Address.fromPublicKey(KeyPair.fromSeed(Base58.encode(("Æ1.0 "+originalSeed).getBytes("UTF-8"))).explicitGet().publicKey).toString
      val a = KeyPair.fromSeed(Base58.encode(("Æ1.0 "+originalSeed).getBytes("UTF-8"))).explicitGet().toAddress.stringRepr
      println (s"a : $a")
      val addr = Address.fromString("Æx3h9vwQ9nguTwqQgjwVT4BitLCHrmiF29dDuLCxNjcpaBmpANeZ")
      println(s"addr : ${addr.explicitGet()}")

      a shouldBe an[String]
      val keys = KeyPair.fromSeed(Base58.encode(("Æ1.0 "+originalSeed).getBytes("UTF-8"))).explicitGet()
      val keys2 = KeyPair.fromSeed(base58Str).explicitGet()
      keys.toAddress.stringRepr should equal("Æx3jE9k1AUpqAegvZQm6BBAQ2Nmm142JAzdoZm7SGMRQEkR5ivfp")
      keys2.toAddress.stringRepr should equal("Æx3h9vwQ9nguTwqQgjwVT4BitLCHrmiF29dDuLCxNjcpaBmpANeZ")
      println(s"keys3 ${"Æ".getBytes("UTF-8").toList}")
      val keys3 = KeyPair.fromSeed(base58str2).explicitGet()
      keys3.toAddress.stringRepr should equal("Æx3h9vwQ9nguTwqQgjwVT4BitLCHrmiF29dDuLCxNjcpaBmpANeZ")
      Address.fromString("Æx3jE9k1AUpqAegvZQm6BBAQ2Nmm142JAzdoZm7SGMRQEkR5ivfp").explicitGet().toString should equal("Æx3jE9k1AUpqAegvZQm6BBAQ2Nmm142JAzdoZm7SGMRQEkR5ivfp")
      //Address.fromString("Æx3h9vwQ9nguTwqQgjwVT4BitLCHrmiF29dDuLCxNjcpaBmpANeZ").explicitGet().toString should equal("Æx3h9vwQ9nguTwqQgjwVT4BitLCHrmiF29dDuLCxNjcpaBmpANeZ")
      Address.fromPublicKey(keys.publicKey, Byte.MinValue).toString should equal("Æx3jE9k1AUpqAegvZQm6BBAQ2Nmm142JAzdoZm7SGMRQEkR5ivfp")
    }

  property("default address import") {
    val seed = "fullmouthedly tilburies accostable v-engine iridoconstrictor innovations amphierotic whomsoever zapateados pledgees victal self-dramatizing jedediah intentionalism hooplike kylander reduced vacuole unrun rightist herried precoccygeal thane yolanda"
    val encodedSeed = Base58.encode((seed).getBytes("UTF-8"))
    val check = KeyPair.fromSeed(encodedSeed).explicitGet().toAddress.stringRepr
    check should equal("ÆxBnTo9ZAr8yyvyPwxZviKA6rMX3Bra2LqdeCGBkqKach7q3rRLnb")
  }

  property("default address import#2") {
    val seed = """yores yellowfish nondestruction puttered ydalir kulla juffer dribber kill-kid maneuverable displacer approximated thunderbearing goggle-nose fable busks untaciturnity transfd wrier prehnitic oppositenesses xysters dallin bsse"""
    val encodedSeed: String = Base58.encode((seed).getBytes("UTF-8"))
    encodedSeed should equal( "2SPp9S5UvVEqsR98NPgDorgPZCrXdFXXXM4eHqx4Qeh6e4MaJpHS76RnkfkFsGRJTE1EGX9EbFsajYAV9KVfRoZ1fkdoyor9TWGRv4v5xyFBTL6pdhiCRs2ARMb4mEkBL1abXD6PJpgfHuBXSyKbmRVcUf2XSEs85wfW2qNF4VVWJicVLHn3rFfi2d8q9shsmVUQNLi3wEBWAgWP84XvjwrzkD4sGRJrTY8okjLMY9edt2gwb5Eai8z2nvJcVvKuwxeB9R7LJjKMby2PJxWFZVW9yDXy3SPktiVDaepJbYUxQA6gKKtp")
    val check = KeyPair.fromSeed(encodedSeed).explicitGet().toAddress.stringRepr
    check should equal("ÆxBmoVTahbmXVf1yowcdiUWmY8tCCWhzJDcv1Q9o1qhU62B7kLWv5")
  }

  property("default address import#3") {
    val seed = """yores yellowfish nondestruction puttered ydalir kulla juffer dribber kill-kid maneuverable displacer approximated thunderbearing goggle-nose fable busks untaciturnity transfd wrier prehnitic oppositenesses xysters dallin bsse"""
    val check = Wallet.generateNewAccount(seed.getBytes("UTF-8"), 0).publicKey.toAddress.stringRepr
    check should equal("ÆxBnqBkNkUYU3npaX787GDXyZQynRACrJJDrHvuZE4RzrDSXBK3HL")
  }

  property("default address import#4") {
    val seed = "fullmouthedly tilburies accostable v-engine iridoconstrictor innovations amphierotic whomsoever zapateados pledgees victal self-dramatizing jedediah intentionalism hooplike kylander reduced vacuole unrun rightist herried precoccygeal thane yolanda"
    val check = Wallet.generateNewAccount(seed.getBytes("UTF-8"), 0).publicKey.toAddress.stringRepr
    check should equal("ÆxBnTo9ZAr8yyvyPwxZviKA6rMX3Bra2LqdeCGBkqKach7q3rRLnb")
  }
}
