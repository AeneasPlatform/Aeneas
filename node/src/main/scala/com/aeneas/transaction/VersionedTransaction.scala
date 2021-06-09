package com.aeneas.transaction

trait VersionedTransaction {
  def version: TxVersion
}
