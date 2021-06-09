package com.aeneas.transaction.assets

import com.aeneas.account.{AddressScheme, KeyPair, PrivateKey, PublicKey}
import com.aeneas.crypto
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction._
import com.aeneas.transaction.serialization.impl.BurnTxSerializer
import com.aeneas.transaction.validation.TxValidator
import com.aeneas.transaction.validation.impl.BurnTxValidator
import monix.eval.Coeval
import play.api.libs.json.JsObject

import scala.util.Try

final case class BurnTransaction(
    version: TxVersion,
    sender: PublicKey,
    asset: IssuedAsset,
    quantity: Long,
    fee: Long,
    timestamp: Long,
    proofs: Proofs,
    chainId: Byte
) extends ProvenTransaction
    with VersionedTransaction
    with SigProofsSwitch
    with TxWithFee.InWaves
    with FastHashId
    with LegacyPBSwitch.V3 {

  override def builder: TransactionParser = BurnTransaction

  override val bodyBytes: Coeval[Array[Byte]] = BurnTxSerializer.bodyBytes(this)
  override val bytes: Coeval[Array[Byte]]     = BurnTxSerializer.toBytes(this)
  override val json: Coeval[JsObject]         = BurnTxSerializer.toJson(this)

  override def checkedAssets: Seq[IssuedAsset] = Seq(asset)
}

object BurnTransaction extends TransactionParser {
  type TransactionT = BurnTransaction

  override val typeId: TxType                    = 6: Byte
  override val supportedVersions: Set[TxVersion] = Set(1, 2, 3)

  implicit val validator: TxValidator[BurnTransaction] = BurnTxValidator

  implicit def sign(tx: BurnTransaction, privateKey: PrivateKey): BurnTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  val serializer = BurnTxSerializer

  override def parseBytes(bytes: Array[TxVersion]): Try[BurnTransaction] =
    serializer.parseBytes(bytes)

  def create(
      version: TxVersion,
      sender: PublicKey,
      asset: IssuedAsset,
      quantity: Long,
      fee: Long,
      timestamp: Long,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, BurnTransaction] =
    BurnTransaction(version, sender, asset, quantity, fee, timestamp, proofs, chainId).validatedEither

  def signed(
      version: TxVersion,
      sender: PublicKey,
      asset: IssuedAsset,
      quantity: Long,
      fee: Long,
      timestamp: Long,
      signer: PrivateKey
  ): Either[ValidationError, BurnTransaction] =
    create(version, sender, asset, quantity, fee, timestamp, Proofs.empty).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      asset: IssuedAsset,
      quantity: Long,
      fee: Long,
      timestamp: Long
  ): Either[ValidationError, BurnTransaction] = {
    signed(version, sender.publicKey, asset, quantity, fee, timestamp, sender.privateKey)
  }
}
