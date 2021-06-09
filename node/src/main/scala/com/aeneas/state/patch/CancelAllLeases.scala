package com.aeneas.state.patch

import com.aeneas.account.{Address, AddressScheme}
import com.aeneas.common.state.ByteStr
import com.aeneas.common.utils._
import com.aeneas.state.{Diff, LeaseBalance, Portfolio}
import play.api.libs.json.Json

case object CancelAllLeases extends DiffPatchFactory {
  val height: Int = AddressScheme.current.chainId.toChar match {
    case 'W' => 462000
    case 'T' => 51500
    case _   => 0
  }

  private[patch] case class CancelledLeases(balances: Map[String, LeaseBalance], cancelledLeases: Set[String])
  private[patch] object CancelledLeases {
    implicit val jsonFormat = Json.format[CancelledLeases]
  }

  def apply(): Diff = {
    val patch = PatchLoader.read[CancelledLeases](this)
    val pfs = patch.balances.map {
      case (address, lb) =>
        Address.fromString(address).explicitGet() -> Portfolio(lease = lb)
    }
    val leasesToCancel = patch.cancelledLeases.map(str => ByteStr.decodeBase58(str).get)

    Diff.empty.copy(portfolios = pfs, leaseState = leasesToCancel.map(_ -> false).toMap)
  }
}
