package com.aeneas.api.http

import akka.http.scaladsl.server.Route
import com.aeneas.account.Address
import com.aeneas.api.common.CommonAssetsApi
import com.aeneas.features.BlockchainFeatures
import com.aeneas.lang.ValidationError
import com.aeneas.state.Blockchain
import com.aeneas.transaction.{TxAmount, TxTimestamp}
import com.aeneas.transaction.TxValidationError.GenericError
import monix.eval.Task
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{Format, Json}

case class RewardApiRoute(blockchain: Blockchain,
                          assetsApi: CommonAssetsApi) extends ApiRoute {
  import RewardApiRoute._

  override lazy val route: Route = pathPrefix("blockchain") {
    path("rewards") {
      rewards() ~ rewardsAtHeight()
    } ~ path("currentReward") {
      currentReward()
    } ~ path("circulatingSupplyAsh") {
      totalAshAmount()
    } ~ path("richList") {
      richList()
    }
  }

  def rewards(): Route = (get & pathEndOrSingleSlash) {
    complete(getRewards(blockchain.height))
  }

  def rewardsAtHeight(): Route = (get & path(IntNumber)) { height =>
    complete(getRewards(height))
  }

  def currentReward(): Route = get {
    complete(getRewards(blockchain.height).map(r => CurrentReward(r.currentReward)))
  }

  def totalAshAmount(): Route = get {
    complete(getRewards(blockchain.height).map(r => TotalAsh(r.totalAshAmount)))
  }

  def getRewards(height: Int): Either[ValidationError, RewardStatus] =
    for {
      _ <- Either.cond(height <= blockchain.height, (), GenericError(s"Invalid height: $height"))
      activatedAt <- blockchain
        .featureActivationHeight(BlockchainFeatures.BlockReward.id)
        .filter(_ <= height)
        .toRight(GenericError("Block reward feature is not activated yet"))
      reward <- blockchain.blockReward(height).toRight(GenericError(s"No information about rewards at height = $height"))
      amount              = blockchain.wavesAmount(height)
      settings            = blockchain.settings.rewardsSettings
      nextCheck           = settings.nearestTermEnd(activatedAt, height)
      votingIntervalStart = nextCheck - settings.votingInterval + 1
      votingThreshold     = settings.votingInterval / 2 + 1
      votes               = blockchain.blockRewardVotes(height).filter(_ >= 0)
    } yield RewardStatus(
      height,
      amount,
      reward,
      settings.minIncrement,
      settings.term,
      nextCheck,
      votingIntervalStart,
      settings.votingInterval,
      votingThreshold,
      RewardVotes(votes.count(_ > reward), votes.count(_ < reward))
    )

  def richList(): Route = get {
    extractScheduler { implicit s =>
      complete(
        addressDistribution(blockchain.height)
        .map(list => list.sortBy(_._2)(Ordering[TxTimestamp].reverse))
        .runToFuture
        .map {l => Json.obj(l.map { case (address, balance) => address.toString -> (balance: JsValueWrapper) }: _*)}
      )
    }
  }

  private def addressDistribution(height: Int):Task[List[(Address, TxAmount)]] = assetsApi
      .wavesDistribution(height, None)
      .toListL
}

object RewardApiRoute {
  final case class RewardStatus(
      height: Int,
      totalAshAmount: BigInt,
      currentReward: Long,
      minIncrement: Long,
      term: Int,
      nextCheck: Int,
      votingIntervalStart: Int,
      votingInterval: Int,
      votingThreshold: Int,
      votes: RewardVotes
  )

  final case class RewardVotes(increase: Int, decrease: Int)
  final case class CurrentReward(currentReward: Long)
  final case class TotalAsh(totalAshAmount: BigInt)

  implicit val rewardVotesFormat: Format[RewardVotes] = Json.format
  implicit val rewardFormat: Format[RewardStatus]     = Json.format
  implicit val currentRewardFormat: Format[CurrentReward] = Json.format
  implicit val totalAshFormat: Format[TotalAsh] = Json.format
}
