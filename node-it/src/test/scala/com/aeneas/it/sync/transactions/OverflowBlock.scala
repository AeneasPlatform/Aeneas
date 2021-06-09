package com.aeneas.it.sync.transactions

import com.aeneas.common.state.ByteStr
import com.aeneas.it.IntegrationSuiteWithThreeAddresses
import com.aeneas.it.sync.calcDataFee
import com.aeneas.state.BinaryDataEntry

import scala.concurrent.duration._

trait OverflowBlock { self: IntegrationSuiteWithThreeAddresses =>

  // Hack to bypass instant micro mining
  def overflowBlock(): Unit = {
    import com.aeneas.it.api.SyncHttpApi._
    val entries = List.tabulate(4)(n => BinaryDataEntry("test" + n, ByteStr(Array.fill(32767)(n.toByte))))
    val addr    = sender.createAddress()
    val fee     = calcDataFee(entries, 1)
    nodes.waitForHeightArise()
    sender.transfer(sender.address, addr, fee * 10, waitForTx = true)
    for (_ <- 1 to 7) sender.putData(addr, entries, fee)
    sender.waitFor("empty utx")(n => n.utxSize, (utxSize: Int) => utxSize == 0, 100.millis)
  }
}
