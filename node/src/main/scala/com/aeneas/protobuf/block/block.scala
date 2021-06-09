package com.aeneas.protobuf

package object block {
  type PBBlock = com.wavesplatform.protobuf.block.Block
  val PBBlock = com.wavesplatform.protobuf.block.Block

  type VanillaBlock = com.aeneas.block.Block
  val VanillaBlock = com.aeneas.block.Block

  type PBBlockHeader = com.wavesplatform.protobuf.block.Block.Header
  val PBBlockHeader = com.wavesplatform.protobuf.block.Block.Header

  type VanillaBlockHeader = com.aeneas.block.BlockHeader
  val VanillaBlockHeader = com.aeneas.block.BlockHeader

  type PBSignedMicroBlock = com.wavesplatform.protobuf.block.SignedMicroBlock
  val PBSignedMicroBlock = com.wavesplatform.protobuf.block.SignedMicroBlock

  type PBMicroBlock = com.wavesplatform.protobuf.block.MicroBlock
  val PBMicroBlock = com.wavesplatform.protobuf.block.MicroBlock

  type VanillaMicroBlock = com.aeneas.block.MicroBlock
  val VanillaMicroBlock = com.aeneas.block.MicroBlock
}
