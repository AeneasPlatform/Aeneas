package com.aeneas.transaction.transfer

import cats.implicits._
import com.aeneas.account._
import com.aeneas.common.state.ByteStr
import com.aeneas.crypto
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.aeneas.transaction.TxValidationError._
import com.aeneas.transaction._
import com.aeneas.transaction.serialization.impl.MassTransferTxSerializer
import com.aeneas.transaction.transfer.MassTransferTransaction.ParsedTransfer
import com.aeneas.transaction.validation.TxValidator
import com.aeneas.transaction.validation.impl.MassTransferTxValidator
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}

import scala.util.{Either, Try}

case class MassTransferTransaction(
    version: TxVersion,
    sender: PublicKey,
    assetId: Asset,
    transfers: Seq[ParsedTransfer],
    fee: TxAmount,
    timestamp: TxTimestamp,
    attachment: ByteStr,
    proofs: Proofs,
    chainId: Byte
) extends ProvenTransaction
    with VersionedTransaction
    with TxWithFee.InWaves
    with FastHashId
    with LegacyPBSwitch.V2 {

  //noinspection TypeAnnotation
  override val builder = MassTransferTransaction

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(builder.serializer.bodyBytes(this))
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(builder.serializer.toBytes(this))
  override val json: Coeval[JsObject]         = Coeval.evalOnce(builder.serializer.toJson(this))

  def compactJson(recipients: Set[AddressOrAlias]): JsObject =
    json() ++ Json.obj("transfers" -> MassTransferTxSerializer.transfersJson(transfers.filter(t => recipients.contains(t.address))))

  override def checkedAssets: Seq[IssuedAsset] = assetId match {
    case Waves          => Seq()
    case a: IssuedAsset => Seq(a)
  }
}

object MassTransferTransaction extends TransactionParser {
  type TransactionT = MassTransferTransaction

  val MaxTransferCount = 100

  override val typeId: TxType                    = 11: Byte
  override val supportedVersions: Set[TxVersion] = Set(1, 2)

  implicit val validator: TxValidator[MassTransferTransaction] = MassTransferTxValidator

  implicit def sign(tx: MassTransferTransaction, privateKey: PrivateKey): MassTransferTransaction =
    tx.copy(proofs = Proofs(crypto.sign(privateKey, tx.bodyBytes())))

  //noinspection TypeAnnotation
  val serializer = MassTransferTxSerializer

  override def parseBytes(bytes: Array[Byte]): Try[MassTransferTransaction] =
    serializer.parseBytes(bytes)

  case class Transfer(
      recipient: String,
      amount: Long
  )

  object Transfer {
    implicit val jsonFormat = Json.format[Transfer]
  }

  case class ParsedTransfer(address: AddressOrAlias, amount: Long)

  def create(
      version: TxVersion,
      sender: PublicKey,
      assetId: Asset,
      transfers: Seq[ParsedTransfer],
      fee: TxAmount,
      timestamp: TxTimestamp,
      attachment: ByteStr,
      proofs: Proofs,
      chainId: Byte = AddressScheme.current.chainId
  ): Either[ValidationError, MassTransferTransaction] =
    MassTransferTransaction(version, sender, assetId, transfers, fee, timestamp, attachment, proofs, chainId).validatedEither

  def signed(
      version: TxVersion,
      sender: PublicKey,
      assetId: Asset,
      transfers: Seq[ParsedTransfer],
      fee: TxAmount,
      timestamp: TxTimestamp,
      attachment: ByteStr,
      signer: PrivateKey
  ): Either[ValidationError, MassTransferTransaction] =
    create(version, sender, assetId, transfers, fee, timestamp, attachment, Proofs.empty).map(_.signWith(signer))

  def selfSigned(
      version: TxVersion,
      sender: KeyPair,
      assetId: Asset,
      transfers: Seq[ParsedTransfer],
      fee: TxAmount,
      timestamp: TxTimestamp,
      attachment: ByteStr
  ): Either[ValidationError, MassTransferTransaction] =
    signed(version, sender.publicKey, assetId, transfers, fee, timestamp, attachment, sender.privateKey)

  def parseTransfersList(transfers: List[Transfer]): Validation[List[ParsedTransfer]] = {
    transfers.traverse {
      case Transfer(recipient, amount) =>
        AddressOrAlias.fromString(recipient).map(ParsedTransfer(_, amount))
    }
  }

}
