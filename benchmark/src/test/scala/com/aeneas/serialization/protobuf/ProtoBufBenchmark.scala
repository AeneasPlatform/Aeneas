package com.aeneas.serialization.protobuf

import java.util.concurrent.TimeUnit

import com.aeneas.account.PublicKey
import com.aeneas.common.state.ByteStr
import com.aeneas.protobuf.transaction.PBTransactions
import com.aeneas.transaction.Asset.Waves
import com.aeneas.transaction.Proofs
import com.aeneas.transaction.transfer.MassTransferTransaction
import com.aeneas.transaction.transfer.MassTransferTransaction.Transfer
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import com.aeneas.common.utils.EitherExt2

//noinspection ScalaStyle
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class ProtoBufBenchmark {

  @Benchmark
  def serializeMassTransferPB_test(bh: Blackhole): Unit = {
    val vanillaTx = {
      val transfers = MassTransferTransaction
        .parseTransfersList(
          List(Transfer("3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh", 100000000L), Transfer("3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh", 200000000L))
        )
        .explicitGet()

      MassTransferTransaction
        .create(
          1.toByte,
          PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
          Waves,
          transfers,
          200000,
          1518091313964L,
          ByteStr.decodeBase58("59QuUcqP6p").get,
          Proofs(Seq(ByteStr.decodeBase58("FXMNu3ecy5zBjn9b69VtpuYRwxjCbxdkZ3xZpLzB8ZeFDvcgTkmEDrD29wtGYRPtyLS3LPYrL2d5UM6TpFBMUGQ").get))
        )
        .explicitGet()
    }

    val tx = PBTransactions.protobuf(vanillaTx)
    bh.consume(tx.toByteArray)
  }

  @Benchmark
  def serializeMassTransferVanilla_test(bh: Blackhole): Unit = {
    val vanillaTx = {
      val transfers = MassTransferTransaction
        .parseTransfersList(
          List(Transfer("3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh", 100000000L), Transfer("3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh", 200000000L))
        )
        .explicitGet()

      MassTransferTransaction
        .create(
          1.toByte,
          PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
          Waves,
          transfers,
          200000,
          1518091313964L,
          ByteStr.decodeBase58("59QuUcqP6p").get,
          Proofs(Seq(ByteStr.decodeBase58("FXMNu3ecy5zBjn9b69VtpuYRwxjCbxdkZ3xZpLzB8ZeFDvcgTkmEDrD29wtGYRPtyLS3LPYrL2d5UM6TpFBMUGQ").get))
        )
        .explicitGet()
    }

    bh.consume(vanillaTx.bytes())
  }
}
