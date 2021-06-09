package com.aeneas.state.patch

import com.aeneas.account.{Address, AddressScheme}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils._
import com.aeneas.state.patch.CancelAllLeases.CancelledLeases
import com.aeneas.state.{Diff, Portfolio}

case object CancelLeaseOverflow extends DiffPatchFactory {
  val height: Int = AddressScheme.current.chainId.toChar match {
    case 'W' => 795000
    case _   => 0
  }

  def apply(): Diff = {
    val patch = PatchLoader.read[CancelledLeases](this)
    val pfs = patch.balances.map {
      case (address, lb) =>
        Address.fromString(address).explicitGet() -> Portfolio(lease = lb)
    }
    val leasesToCancel = patch.cancelledLeases.map(str => ByteStr.decodeBase58(str).get)
    val diff           = Diff.empty.copy(portfolios = pfs, leaseState = leasesToCancel.map(_ -> false).toMap)
    diff
  }
}
