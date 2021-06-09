package com.aeneas.state

import com.aeneas.account.Address
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils.EitherExt2
import com.aeneas.crypto.SignatureLength
import com.aeneas.db.WithDomain
import com.aeneas.lagonaki.mocks.TestBlock
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.GenesisTransaction
import com.aeneas.{NoShrink, TestTime, TransactionGen}
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class CommonSpec extends FreeSpec with Matchers with WithDomain with TransactionGen with PropertyChecks with NoShrink {
  private val time          = new TestTime
  private def nextTs        = time.getTimestamp()
  private val AssetIdLength = 32

  private def genesisBlock(genesisTs: Long, address: Address, initialBalance: Long) = TestBlock.create(
    genesisTs,
    ByteStr(Array.fill[Byte](SignatureLength)(0)),
    Seq(GenesisTransaction.create(address, initialBalance, genesisTs).explicitGet())
  )

  "Common Conditions" - {
    "Zero balance of absent asset" in forAll(accountGen, positiveLongGen, byteArrayGen(AssetIdLength)) {
      case (sender, initialBalance, assetId) =>
        withDomain() { d =>
          d.appendBlock(genesisBlock(nextTs, sender.toAddress, initialBalance))
          d.balance(sender.toAddress, IssuedAsset(ByteStr(assetId))) shouldEqual 0L
        }
    }
  }
}
