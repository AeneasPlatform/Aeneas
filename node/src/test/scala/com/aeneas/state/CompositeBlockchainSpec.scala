package com.aeneas.state

import com.aeneas.state.reader.CompositeBlockchain
import com.aeneas.utils.EmptyBlockchain
import com.aeneas.{BlockGen, NoShrink}
import org.scalatest.{FreeSpec, Matchers}
import org.scalatestplus.scalacheck.{ScalaCheckPropertyChecks => PropertyChecks}

class CompositeBlockchainSpec extends FreeSpec with Matchers with PropertyChecks with BlockGen with NoShrink {
  "blockHeaderAndSize at current height is last block" in forAll(randomSignerBlockGen) { block =>
    val comp = CompositeBlockchain(EmptyBlockchain, newBlock = Some(block))

    comp.blockHeader(comp.height).map(_.id()) should contain(block.id())
  }
}
