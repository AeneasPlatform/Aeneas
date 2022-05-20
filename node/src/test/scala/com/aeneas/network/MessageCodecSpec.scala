package com.aeneas.network

import java.nio.charset.StandardCharsets

import com.aeneas.TransactionGen
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.{ProvenTransaction, Transaction}
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class MessageCodecSpec extends FreeSpec with Matchers with MockFactory with PropertyChecks with TransactionGen {

  "should block a sender of invalid messages" in {
    val codec = new SpiedMessageCodec
    val ch    = new EmbeddedChannel(codec)

    ch.writeInbound(RawBytes(TransactionSpec.messageCode, "foo".getBytes(StandardCharsets.UTF_8)))
    ch.readInbound[IssueTransaction]()

    codec.blockCalls shouldBe 1
  }

  "should not block a sender of valid messages" in forAll(randomTransactionGen) { origTx: ProvenTransaction =>
    val codec = new SpiedMessageCodec
    val ch    = new EmbeddedChannel(codec)

    ch.writeInbound(RawBytes.fromTransaction(origTx))
    val decodedTx = ch.readInbound[Transaction]()

    decodedTx shouldBe origTx
    codec.blockCalls shouldBe 0
  }

  private class SpiedMessageCodec extends MessageCodec(PeerDatabase.NoOp) {
    var blockCalls = 0

    override def block(ctx: ChannelHandlerContext, e: Throwable): Unit = {
      blockCalls += 1
    }
  }

}
