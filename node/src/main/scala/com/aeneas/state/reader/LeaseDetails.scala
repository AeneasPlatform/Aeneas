package com.aeneas.state.reader

import com.aeneas.account.{PublicKey, AddressOrAlias}

case class LeaseDetails(sender: PublicKey, recipient: AddressOrAlias, height: Int, amount: Long, isActive: Boolean)
