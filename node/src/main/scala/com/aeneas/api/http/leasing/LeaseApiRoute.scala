package com.aeneas.api.http.leasing

import akka.http.scaladsl.server.Route
import com.aeneas.api.common.CommonAccountsApi
import com.aeneas.api.http._
import com.aeneas.api.http.requests.{LeaseCancelRequest, LeaseRequest}
import com.aeneas.http.BroadcastRoute
import com.aeneas.network.UtxPoolSynchronizer
import com.aeneas.settings.RestAPISettings
import com.aeneas.state.Blockchain
import com.aeneas.transaction._
import com.aeneas.transaction.lease.LeaseTransaction
import com.aeneas.utils.Time
import com.aeneas.wallet.Wallet
import play.api.libs.json.JsNumber

case class LeaseApiRoute(
    settings: RestAPISettings,
    wallet: Wallet,
    blockchain: Blockchain,
    utxPoolSynchronizer: UtxPoolSynchronizer,
    time: Time,
    commonAccountApi: CommonAccountsApi
) extends ApiRoute
    with BroadcastRoute
    with AuthRoute {

  override val route: Route = pathPrefix("leasing") {
    active ~ deprecatedRoute
  }

  private def deprecatedRoute: Route =
    (path("lease") & withAuth) {
      broadcast[LeaseRequest](TransactionFactory.lease(_, wallet, time))
    } ~ (path("cancel") & withAuth) {
      broadcast[LeaseCancelRequest](TransactionFactory.leaseCancel(_, wallet, time))
    } ~ pathPrefix("broadcast") {
      path("lease")(broadcast[LeaseRequest](_.toTx)) ~
        path("cancel")(broadcast[LeaseCancelRequest](_.toTx))
    }

  def active: Route = (pathPrefix("active") & get & extractScheduler) { implicit sc =>
    path(AddrSegment) { address =>
      complete(
        commonAccountApi
          .activeLeases(address)
          .collect {
            case (height, leaseTransaction: LeaseTransaction) =>
              leaseTransaction.json() + ("height" -> JsNumber(height))
          }
          .toListL
          .runToFuture
      )
    }
  }
}
