package com.aeneas.api.common

import com.aeneas.WithDB
import org.scalatest.FreeSpec

class AddressTransactionsSpec extends FreeSpec with WithDB {
  "addressTransactions" - {
    "without pagination" in pending

    "with pagination" - {
      "after txs is in the middle of ngState" in pending
      "after txs is the last of ngState" in pending
      "after txs is in levelDb" in pending
    }

    "return txs in correct ordering without fromId" in pending

    "correctly applies transaction type filter" in pending

    "return Left if fromId argument is a non-existent transaction" in pending

    "return txs in correct ordering starting from a given id" in pending

    "return an empty Seq when paginating from the last transaction" in pending
  }
}
