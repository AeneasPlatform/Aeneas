package com.aeneas

import java.io.File

import com.google.common.primitives.Ints
import com.google.protobuf.ByteString
import com.aeneas.account.{Address, AddressScheme, KeyPair}
import com.aeneas.block.Block
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils._
import com.aeneas.database.{LevelDBWriter, openDB}
import com.aeneas.protobuf.transaction.PBRecipients
import com.aeneas.state.{Diff, Portfolio}
import com.aeneas.transaction.Asset.IssuedAsset
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.{GenesisTransaction, Proofs}
import com.aeneas.utils.{NTP, ScorexLogging}
import monix.reactive.Observer

object RollbackBenchmark extends ScorexLogging {
  def main(args: Array[String]): Unit = {
    val settings      = Application.loadApplicationConfig(Some(new File(args(0))))
    val db            = openDB(settings.dbSettings.directory)
    val time          = new NTP(settings.ntpServer)
    val levelDBWriter = LevelDBWriter(db, Observer.stopped, settings)

    val issuer = KeyPair(new Array[Byte](32))

    log.info("Generating addresses")

    val addresses = 1 to 18000 map { i =>
      PBRecipients.toAddress(Ints.toByteArray(i) ++ new Array[Byte](Address.HashLength - 4), AddressScheme.current.chainId).explicitGet()
    }

    log.info("Generating issued assets")

    val assets = 1 to 200 map { i =>
      IssueTransaction(
        1.toByte,
        issuer.publicKey,
        ByteString.copyFromUtf8("asset-" + i),
        ByteString.EMPTY,
        100000e2.toLong,
        2.toByte,
        false,
        None,
        1e8.toLong,
        time.getTimestamp(),
        Proofs(ByteStr(new Array[Byte](64))),
        AddressScheme.current.chainId
      )
    }

    log.info("Building genesis block")
    val genesisBlock = Block
      .buildAndSign(
        1.toByte,
        time.getTimestamp(),
        Block.GenesisReference,
        1000,
        Block.GenesisGenerationSignature,
        GenesisTransaction.create(issuer.publicKey.toAddress, 100000e8.toLong, time.getTimestamp()).explicitGet() +: assets,
        issuer,
        Seq.empty,
        -1
      )
      .explicitGet()

    val map = assets.map(it => IssuedAsset(it.id()) -> 1L).toMap
    val portfolios = for {
      address <- addresses
    } yield address -> Portfolio(assets = map)

    log.info("Appending genesis block")
    levelDBWriter.append(
      Diff.empty.copy(portfolios = portfolios.toMap),
      0,
      0,
      None,
      genesisBlock.header.generationSignature,
      genesisBlock
    )

    val nextBlock =
      Block
        .buildAndSign(2.toByte, time.getTimestamp(), genesisBlock.id(), 1000, Block.GenesisGenerationSignature, Seq.empty, issuer, Seq.empty, -1)
        .explicitGet()
    val nextDiff = Diff.empty.copy(portfolios = addresses.map(_ -> Portfolio(1, assets = Map(IssuedAsset(assets.head.id()) -> 1L))).toMap)

    log.info("Appending next block")
    levelDBWriter.append(nextDiff, 0, 0, None, ByteStr.empty, nextBlock)

    log.info("Rolling back")
    val start = System.nanoTime()
    levelDBWriter.rollbackTo(genesisBlock.id())
    val end = System.nanoTime()
    log.info(f"Rollback took ${(end - start) * 1e-6}%.3f ms")
    levelDBWriter.close()
  }
}
