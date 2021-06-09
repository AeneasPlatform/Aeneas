package com.aeneas.it.sync.utils

import com.aeneas.account.{Address, PublicKey}
import com.aeneas.common.state.ByteStr
import com.aeneas.it.api.SyncHttpApi._
import com.aeneas.it.sync._
import com.aeneas.it.transactions.BaseTransactionSuite
import com.aeneas.it.util._
import com.aeneas.lang.script.Script
import com.aeneas.lang.v1.FunctionHeader
import com.aeneas.lang.v1.compiler.Terms
import com.aeneas.lang.v1.compiler.Terms.TRUE
import com.aeneas.state.{BinaryDataEntry, BooleanDataEntry, IntegerDataEntry}
import com.aeneas.transaction.Asset.{IssuedAsset, Waves}
import com.aeneas.transaction.assets._
import com.aeneas.transaction.assets.exchange._
import com.aeneas.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.aeneas.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.aeneas.transaction.transfer.MassTransferTransaction.Transfer
import com.aeneas.transaction.transfer.{MassTransferTransaction, TransferTransaction}
import com.aeneas.transaction.{CreateAliasTransaction, DataTransaction, Proofs, Transaction, TxVersion}
import com.aeneas.utils._
import org.scalatest.prop.TableDrivenPropertyChecks
import com.aeneas.common.utils.EitherExt2

class TransactionSerializeSuite extends BaseTransactionSuite with TableDrivenPropertyChecks {
  private val publicKey         = PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet()
  private val ts: Long          = 1526287561757L
  private val tsOrderFrom: Long = 1526992336241L
  private val tsOrderTo: Long   = 1529584336241L

  private val buyV2 = Order(
    TxVersion.V2,
    PublicKey.fromBase58String("BqeJY8CP3PeUDaByz57iRekVUGtLxoow4XxPvXfHynaZ").explicitGet(),
    PublicKey.fromBase58String("Fvk5DXmfyWVZqQVBowUBMwYtRAHDtdyZNNeRrwSjt6KP").explicitGet(),
    AssetPair.createAssetPair("WAVES", "9ZDWzK53XT5bixkmMwTJi2YzgxCqn5dUajXFcT2HcFDy").get,
    OrderType.BUY,
    2,
    60.waves,
    tsOrderFrom,
    tsOrderTo,
    1,
    proofs = Proofs(Seq(ByteStr.decodeBase58("2bkuGwECMFGyFqgoHV4q7GRRWBqYmBFWpYRkzgYANR4nN2twgrNaouRiZBqiK2RJzuo9NooB9iRiuZ4hypBbUQs").get))
  )

  val buyV1 = Order(
    TxVersion.V1,
    PublicKey.fromBase58String("BqeJY8CP3PeUDaByz57iRekVUGtLxoow4XxPvXfHynaZ").explicitGet(),
    PublicKey.fromBase58String("Fvk5DXmfyWVZqQVBowUBMwYtRAHDtdyZNNeRrwSjt6KP").explicitGet(),
    AssetPair.createAssetPair("WAVES", "9ZDWzK53XT5bixkmMwTJi2YzgxCqn5dUajXFcT2HcFDy").get,
    OrderType.BUY,
    2,
    60.waves,
    tsOrderFrom,
    tsOrderTo,
    1,
    proofs = Proofs(ByteStr.decodeBase58("2bkuGwECMFGyFqgoHV4q7GRRWBqYmBFWpYRkzgYANR4nN2twgrNaouRiZBqiK2RJzuo9NooB9iRiuZ4hypBbUQs").get)
  )

  private val sell = Order(
    TxVersion.V1,
    PublicKey.fromBase58String("7E9Za8v8aT6EyU1sX91CVK7tWUeAetnNYDxzKZsyjyKV").explicitGet(),
    PublicKey.fromBase58String("Fvk5DXmfyWVZqQVBowUBMwYtRAHDtdyZNNeRrwSjt6KP").explicitGet(),
    AssetPair.createAssetPair("WAVES", "9ZDWzK53XT5bixkmMwTJi2YzgxCqn5dUajXFcT2HcFDy").get,
    OrderType.SELL,
    3,
    50.waves,
    tsOrderFrom,
    tsOrderTo,
    2,
    proofs = Proofs(ByteStr.decodeBase58("2R6JfmNjEnbXAA6nt8YuCzSf1effDS4Wkz8owpCD9BdCNn864SnambTuwgLRYzzeP5CAsKHEviYKAJ2157vdr5Zq").get)
  )

  private val exV1 = ExchangeTransaction
    .create(
      TxVersion.V1,
      buyV1,
      sell,
      2,
      50.waves,
      1,
      1,
      1,
      tsOrderFrom,
      Proofs(ByteStr.decodeBase58("5NxNhjMrrH5EWjSFnVnPbanpThic6fnNL48APVAkwq19y2FpQp4tNSqoAZgboC2ykUfqQs9suwBQj6wERmsWWNqa").get)
    )
    .explicitGet()

  private val exV2 = ExchangeTransaction
    .create(
      TxVersion.V2,
      buyV2,
      sell,
      2,
      50.waves,
      1,
      1,
      1,
      tsOrderFrom,
      Proofs(Seq(ByteStr.decodeBase58("5NxNhjMrrH5EWjSFnVnPbanpThic6fnNL48APVAkwq19y2FpQp4tNSqoAZgboC2ykUfqQs9suwBQj6wERmsWWNqa").get))
    )
    .explicitGet()

  private val burnV1 = BurnTransaction
    .create(
      1.toByte,
      publicKey,
      IssuedAsset(ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get),
      10000000000L,
      burnFee,
      ts,
      Proofs(ByteStr.decodeBase58("uapJcAJQryBhWThU43rYgMNmvdT7kY747vx5BBgxr2KvaeTRx8Vsuh4yu1JxBymU9LnAoo1zjQcPrWSuhi6dVPE").get)
    )
    .explicitGet()

  private val burnV2 = BurnTransaction
    .create(
      2.toByte,
      publicKey,
      IssuedAsset(ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get),
      10000000000L,
      burnFee,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("3NcEv6tcVMuXkTJwiqW4J3GMCTe8iSLY7neEfNZonp59eTQEZXYPQWs565CRUctDrvcbtmsRgWvnN7BnFZ1AVZ1H").get))
    )
    .explicitGet()

  private val aliasV1 = CreateAliasTransaction
    .create(
      Transaction.V1,
      publicKey,
      "myalias",
      minFee,
      ts,
      Proofs(ByteStr.decodeBase58("CC1jQ4qkuVfMvB2Kpg2Go6QKXJxUFC8UUswUxBsxwisrR8N5s3Yc8zA6dhjTwfWKfdouSTAnRXCxTXb3T6pJq3T").get)
    )
    .explicitGet()

  private val aliasV2 = CreateAliasTransaction
    .create(
      Transaction.V2,
      publicKey,
      "myalias",
      minFee,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("26U7rQTwpdma5GYSZb5bNygVCtSuWL6DKet1Nauf5J57v19mmfnq434YrkKYJqvYt2ydQBUT3P7Xgj5ZVDVAcc5k").get))
    )
    .explicitGet()

  private val data = DataTransaction
    .create(
      1.toByte,
      publicKey,
      List(IntegerDataEntry("int", 24), BooleanDataEntry("bool", true), BinaryDataEntry("blob", ByteStr.decodeBase64("YWxpY2U=").get)),
      minFee,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("32mNYSefBTrkVngG5REkmmGAVv69ZvNhpbegmnqDReMTmXNyYqbECPgHgXrX2UwyKGLFS45j7xDFyPXjF8jcfw94").get))
    )
    .explicitGet()

  private val issueV1 = IssueTransaction(
    TxVersion.V1,
    publicKey,
    "Gigacoin".utf8Bytes,
    "Gigacoin".utf8Bytes,
    someAssetAmount,
    8.toByte,
    true,
    script = None,
    issueFee,
    ts,
    Proofs(ByteStr.decodeBase58("28kE1uN1pX2bwhzr9UHw5UuB9meTFEDFgeunNgy6nZWpHX4pzkGYotu8DhQ88AdqUG6Yy5wcXgHseKPBUygSgRMJ").get)
  )

  private val issueV2 = IssueTransaction(
    TxVersion.V2,
    publicKey,
    "Gigacoin".utf8Bytes,
    "Gigacoin".utf8Bytes,
    someAssetAmount,
    8.toByte,
    true,
    None,
    issueFee,
    ts,
    Proofs(Seq(ByteStr.decodeBase58("43TCfWBa6t2o2ggsD4bU9FpvH3kmDbSBWKE1Z6B5i5Ax5wJaGT2zAvBihSbnSS3AikZLcicVWhUk1bQAMWVzTG5g").get))
  )

  private val leasecancelV1 = LeaseCancelTransaction
    .create(
      1.toByte,
      publicKey,
      ByteStr.decodeBase58("EXhjYjy8a1dURbttrGzfcft7cddDnPnoa3vqaBLCTFVY").get,
      minFee,
      ts,
      Proofs(ByteStr.decodeBase58("4T76AXcksn2ixhyMNu4m9UyY54M3HDTw5E2HqUsGV4phogs2vpgBcN5oncu4sbW4U3KU197yfHMxrc3kZ7e6zHG3").get)
    )
    .explicitGet()

  private val leasecancelV2 = LeaseCancelTransaction
    .create(
      2.toByte,
      publicKey,
      ByteStr.decodeBase58("DJWkQxRyJNqWhq9qSQpK2D4tsrct6eZbjSv3AH4PSha6").get,
      minFee,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("3h5SQLbCzaLoTHUeoCjXUHB6qhNUfHZjQQVsWTRAgTGMEdK5aeULMVUfDq63J56kkHJiviYTDT92bLGc8ELrUgvi").get))
    )
    .explicitGet()

  private val leaseV1 = LeaseTransaction
    .create(
      1.toByte,
      publicKey,
      Address.fromString(sender.address).explicitGet(),
      10000000,
      minFee,
      ts,
      Proofs(ByteStr.decodeBase58("iy3TmfbFds7pc9cDDqfjEJhfhVyNtm3GcxoVz8L3kJFvgRPUmiqqKLMeJGYyN12AhaQ6HvE7aF1tFgaAoCCgNJJ").get)
    )
    .explicitGet()

  private val leaseV2 = LeaseTransaction
    .create(
      2.toByte,
      publicKey,
      Address.fromString(sender.address).explicitGet(),
      10000000,
      minFee,
      ts,
      Proofs(ByteStr.decodeBase58("5Fr3yLwvfKGDsFLi8A8JbHqToHDojrPbdEGx9mrwbeVWWoiDY5pRqS3rcX1rXC9ud52vuxVdBmGyGk5krcgwFu9q").get)
    )
    .explicitGet()

  private val transfers = MassTransferTransaction
    .parseTransfersList(List(Transfer(firstAddress, 1.waves), Transfer(secondAddress, 2.waves)))
    .explicitGet()

  val mass = MassTransferTransaction
    .create(
      1.toByte,
      publicKey,
      Waves,
      transfers,
      2.waves,
      ts,
      ByteStr.decodeBase58("59QuUcqP6p").get,
      Proofs(Seq(ByteStr.decodeBase58("FXMNu3ecy5zBjn9b69VtpuYRwxjCbxdkZ3xZpLzB8ZeFDvcgTkmEDrD29wtGYRPtyLS3LPYrL2d5UM6TpFBMUGQ").get))
    )
    .explicitGet()

  private val reissueV1 = ReissueTransaction
    .create(
      1.toByte,
      publicKey,
      IssuedAsset(ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get),
      100000000L,
      true,
      1.waves,
      ts,
      Proofs(ByteStr.decodeBase58("3LnRMrjkk7RoV35PTwcdB4yW2rqUqXaKAh8DnPk5tNWABvhVQ9oqdTk3zM8b9AbGtry7WEcQZtevfK92DCFaa6hA").get)
    )
    .explicitGet()

  private val reissueV2 = ReissueTransaction
    .create(
      2.toByte,
      publicKey,
      IssuedAsset(ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get),
      100000000L,
      true,
      1.waves,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("4DFEtUwJ9gjMQMuEXipv2qK7rnhhWEBqzpC3ZQesW1Kh8D822t62e3cRGWNU3N21r7huWnaty95wj2tZxYSvCfro").get))
    )
    .explicitGet()

  private val setasset = SetAssetScriptTransaction
    .create(
      1.toByte,
      publicKey,
      IssuedAsset(ByteStr.decodeBase58("DUyJyszsWcmZG7q2Ctk1hisDeGBPB8dEzyU8Gs5V2j3n").get),
      Some(Script.fromBase64String("base64:AQkAAGcAAAACAHho/EXujJiPAJUhuPXZYac+rt2jYg==").explicitGet()),
      1.waves,
      ts,
      Proofs(
        Seq(
          "5sRtXKcdDa",
          "9Zfe5aw9D7rRR3nvU3QuAjCNT7pdwRXwvBFxHmdt2WtWwiEwffn",
          "",
          "3C",
          "24jboCkAEFrsBKNh6z8FFyJP8YhejsrBwt7JdHVhiCk7DCc3Zxsc4g6PYG8tsLXmK",
          ""
        ).map(ByteStr.decodeBase58(_).get)
      )
    )
    .explicitGet()

  private val setscript = SetScriptTransaction
    .create(
      1.toByte,
      publicKey,
      None,
      setScriptFee,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("tcTr672rQ5gXvcA9xCGtQpkHC8sAY1TDYqDcQG7hQZAeHcvvHFo565VEv1iD1gVa3ZuGjYS7hDpuTnQBfY2dUhY").get))
    )
    .explicitGet()

  private val sponsor = SponsorFeeTransaction
    .create(
      1.toByte,
      publicKey,
      IssuedAsset(ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get),
      Some(100000),
      1.waves,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("3QrF81WkwGhbNvKcwpAVyBPL1MLuAG5qmR6fmtK9PTYQoFKGsFg1Rtd2kbMBuX2ZfiFX58nR1XwC19LUXZUmkXE7").get))
    )
    .explicitGet()

  private val recipient = Address.fromString(sender.address).explicitGet()
  private val transferV1 = TransferTransaction(
    1.toByte,
    publicKey,
    recipient,
    Waves,
    1900000,
    Waves,
    minFee,
    ByteStr.empty,
    ts,
    Proofs(Seq(ByteStr.decodeBase58("eaV1i3hEiXyYQd6DQY7EnPg9XzpAvB9VA3bnpin2qJe4G36GZXaGnYKCgSf9xiQ61DcAwcBFzjSXh6FwCgazzFz").get)),
    recipient.chainId
  )

  private val transferV2 = TransferTransaction(
    2.toByte,
    publicKey,
    recipient,
    Waves,
    100000000,
    Waves,
    minFee,
    ByteStr.empty,
    ts,
    Proofs(Seq(ByteStr.decodeBase58("4bfDaqBcnK3hT8ywFEFndxtS1DTSYfncUqd4s5Vyaa66PZHawtC73rDswUur6QZu5RpqM7L9NFgBHT1vhCoox4vi").get)),
    recipient.chainId
  )

  private val invokeScript = InvokeScriptTransaction
    .create(
      1.toByte,
      PublicKey.fromBase58String("BqeJY8CP3PeUDaByz57iRekVUGtLxoow4XxPvXfHynaZ").explicitGet(),
      PublicKey.fromBase58String("Fvk5DXmfyWVZqQVBowUBMwYtRAHDtdyZNNeRrwSjt6KP").explicitGet().toAddress,
      Some(
        Terms.FUNCTION_CALL(
          function = FunctionHeader.User("testfunc"),
          args = List(TRUE)
        )
      ),
      Seq(InvokeScriptTransaction.Payment(7, IssuedAsset(ByteStr.decodeBase58("73pu8pHFNpj9tmWuYjqnZ962tXzJvLGX86dxjZxGYhoK").get))),
      smartMinFee,
      Waves,
      ts,
      Proofs(Seq(ByteStr.decodeBase58("4bfDaqBcnK3hT8ywFEFndxtS1DTSYfncUqd4s5Vyaa66PZHawtC73rDswUur6QZu5RpqM7L9NFgBHT1vhCoox4vi").get))
    )
    .explicitGet()

  forAll(
    Table(
      ("tx", "name"),
      (exV1, "exchangeV1"),
      (exV2, "exchangeV2"),
      (burnV1, "burnV1"),
      (burnV2, "burnV2"),
      (aliasV1, "aliasV1"),
      (aliasV2, "aliasV2"),
      (data, "data"),
      (issueV1, "issueV1"),
      (issueV2, "issueV2"),
      (leasecancelV1, "leasecancelV1"),
      (leasecancelV2, "leasecancelV2"),
      (leaseV1, "leaseV1"),
      (leaseV2, "leaseV2"),
      (mass, "mass"),
      (reissueV1, "reissueV1"),
      (reissueV2, "reissueV2"),
      (setasset, "setasset"),
      (setscript, "setscript"),
      (sponsor, "sponsor"),
      (transferV1, "transferV1"),
      (transferV2, "transferV2"),
      (invokeScript, "invokeScript")
    )
  ) { (tx, name) =>
    test(s"Serialize check of $name transaction") {
      val r = sender.transactionSerializer(tx.json()).bytes.map(_.toByte)
      r shouldBe tx.bodyBytes()
    }
  }
}
