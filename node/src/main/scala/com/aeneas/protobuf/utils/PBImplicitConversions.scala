package com.aeneas.protobuf.utils
import com.aeneas.account.PublicKey
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ValidationError
import com.aeneas.protobuf.transaction.{PBAmounts, PBRecipients, VanillaAssetId}
import com.aeneas.transaction.Asset
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.google.protobuf.ByteString
import com.wavesplatform.protobuf.Amount
import com.wavesplatform.protobuf.transaction._

object PBImplicitConversions {
  import com.aeneas.account.{AddressOrAlias, Address => VAddress, Alias => VAlias}
  import com.google.protobuf.{ByteString => PBByteString}

  implicit def fromAddressOrAlias(addressOrAlias: AddressOrAlias): Recipient = PBRecipients.create(addressOrAlias)
  implicit def fromAddress(address: VAddress): PBByteString                  = PBByteString.copyFrom(address.bytes)

  implicit class PBRecipientImplicitConversionOps(recipient: Recipient) {
    def toAddress(chainId: Byte): Either[ValidationError, VAddress]              = PBRecipients.toAddress(recipient, chainId)
    def toAlias(chainId: Byte): Either[ValidationError, VAlias]                  = PBRecipients.toAlias(recipient, chainId)
    def toAddressOrAlias(chainId: Byte): Either[ValidationError, AddressOrAlias] = PBRecipients.toAddressOrAlias(recipient, chainId)
  }

  implicit class ByteStrExt(val bs: ByteStr) extends AnyVal {
    def toByteString: PBByteString = ByteString.copyFrom(bs.arr)
  }

  implicit class StrExt(val bs: String) extends AnyVal {
    def toByteString: PBByteString = ByteString.copyFrom(bs.getBytes("UTF-8"))
  }

  implicit class ByteStringExt(val bs: ByteString) extends AnyVal {
    def toByteStr: ByteStr = ByteStr(bs.toByteArray)
  }

  implicit def fromAssetIdAndAmount(v: (VanillaAssetId, Long)): Amount = v match {
    case (IssuedAsset(assetId), amount) =>
      Amount()
        .withAssetId(assetId.toByteString)
        .withAmount(amount)

    case (Waves, amount) =>
      Amount().withAmount(amount)
  }

  implicit class AmountImplicitConversions(a: Amount) {
    def longAmount: Long      = a.amount
    def vanillaAssetId: Asset = PBAmounts.toVanillaAssetId(a.assetId)
  }

  implicit class PBByteStringOps(bs: PBByteString) {
    def byteStr: ByteStr            = ByteStr(bs.toByteArray)
    def publicKeyAccount: PublicKey = PublicKey(bs.toByteArray)
  }

  implicit def byteStringToByte(bytes: ByteString): Byte =
    if (bytes.isEmpty) 0
    else bytes.byteAt(0)

  implicit def byteToByteString(chainId: Byte): ByteString = {
    if (chainId == 0) ByteString.EMPTY else ByteString.copyFrom(Array(chainId))
  }
}
