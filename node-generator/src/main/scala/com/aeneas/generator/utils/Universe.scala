package com.aeneas.generator.utils

import com.aeneas.generator.Preconditions.CreatedAccount
import com.aeneas.transaction.assets.IssueTransaction
import com.aeneas.transaction.lease.LeaseTransaction

object Universe {
  @volatile var Accounts: List[CreatedAccount]       = Nil
  @volatile var IssuedAssets: List[IssueTransaction] = Nil
  @volatile var Leases: List[LeaseTransaction]       = Nil
}
