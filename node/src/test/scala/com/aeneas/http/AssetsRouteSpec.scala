package com.aeneas.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.aeneas.account.Address
import com.aeneas.api.common.{CommonAccountsApi, CommonAssetsApi}
import com.aeneas.api.http.assets.AssetsApiRoute
import com.aeneas.api.http.requests.{TransferV1Request, TransferV2Request}
import com.aeneas.http.ApiMarshallers._
import com.aeneas.state.Blockchain
import com.aeneas.transaction.transfer._
import com.aeneas.wallet.Wallet
import com.aeneas.{RequestGen, TestTime}
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Writes

class AssetsRouteSpec extends RouteSpec("/assets") with RequestGen with PathMockFactory with Eventually with RestAPISettingsHelper {

  private val wallet = stub[Wallet]
  private val state  = stub[Blockchain]

  private val seed               = "seed".getBytes("UTF-8")
  private val senderPrivateKey   = Wallet.generateNewAccount(seed, 0)
  private val receiverPrivateKey = Wallet.generateNewAccount(seed, 1)

  (wallet.privateKeyAccount _).when(senderPrivateKey.toAddress).onCall((_: Address) => Right(senderPrivateKey)).anyNumberOfTimes()

  "/transfer" - {
    val route: Route = AssetsApiRoute(
      restAPISettings,
      wallet,
      DummyUtxPoolSynchronizer.accepting,
      state,
      new TestTime(),
      mock[CommonAccountsApi],
      mock[CommonAssetsApi]
    ).route

    def posting[A: Writes](v: A): RouteTestResult = Post(routePath("/transfer"), v).addHeader(ApiKeyHeader) ~> route

    "accepts TransferRequest" in {
      val req = TransferV1Request(
        assetId = None,
        feeAssetId = None,
        amount = 1 * Waves,
        fee = Waves / 3,
        sender = senderPrivateKey.toAddress.toString,
        attachment = Some("attachment"),
        recipient = receiverPrivateKey.toAddress.toString,
        timestamp = Some(System.currentTimeMillis())
      )

      posting(req) ~> check {
        status shouldBe StatusCodes.OK

        responseAs[TransferTransaction]
      }
    }

    "accepts VersionedTransferRequest" in {
      val req = TransferV2Request(
        assetId = None,
        amount = 1 * Waves,
        feeAssetId = None,
        fee = Waves / 3,
        sender = senderPrivateKey.toAddress.toString,
        attachment = None,
        recipient = receiverPrivateKey.toAddress.toString,
        timestamp = Some(System.currentTimeMillis())
      )

      posting(req) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[TransferV2Request]
      }
    }

    "returns a error if it is not a transfer request" in {
      val req = issueReq.sample.get
      posting(req) ~> check {
        status shouldNot be(StatusCodes.OK)
      }
    }
  }

}
