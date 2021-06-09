package com.aeneas.protobuf

package object transaction {
  type PBOrder = com.wavesplatform.protobuf.order.Order
  val PBOrder = com.wavesplatform.protobuf.order.Order

  type VanillaOrder = com.aeneas.transaction.assets.exchange.Order
  val VanillaOrder = com.aeneas.transaction.assets.exchange.Order

  type PBTransaction = com.wavesplatform.protobuf.transaction.Transaction
  val PBTransaction = com.wavesplatform.protobuf.transaction.Transaction

  type PBSignedTransaction = com.wavesplatform.protobuf.transaction.SignedTransaction
  val PBSignedTransaction = com.wavesplatform.protobuf.transaction.SignedTransaction

  type VanillaTransaction = com.aeneas.transaction.Transaction
  val VanillaTransaction = com.aeneas.transaction.Transaction

  type VanillaSignedTransaction = com.aeneas.transaction.SignedTransaction

  type VanillaAssetId = com.aeneas.transaction.Asset
}
