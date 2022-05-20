package com.aeneas.features.api

import akka.http.scaladsl.server.Route
import com.aeneas.api.http.ApiRoute
import com.aeneas.features.{BlockchainFeatureStatus, BlockchainFeatures}
import com.aeneas.settings.{FeaturesSettings, RestAPISettings}
import com.aeneas.state.Blockchain
import play.api.libs.json.Json

case class ActivationApiRoute(settings: RestAPISettings, featuresSettings: FeaturesSettings, blockchain: Blockchain) extends ApiRoute {

  override lazy val route: Route = pathPrefix("activation") {
    status
  }

  def status: Route = (get & path("status")) {
    val height = blockchain.height

    val featureIds = (blockchain.featureVotes(height).keySet ++
      blockchain.approvedFeatures.keySet ++
      BlockchainFeatures.implemented).toSeq.sorted

    complete(
      Json.toJson(
        ActivationStatus(
          height,
          blockchain.settings.functionalitySettings.activationWindowSize(height),
          blockchain.settings.functionalitySettings.blocksForFeatureActivation(height),
          blockchain.settings.functionalitySettings.activationWindow(height).last,
          featureIds.map { id =>
            val status = blockchain.featureStatus(id, height)
            val voted = featuresSettings.supported.contains(id) && !blockchain.activatedFeatures
              .get(id)
              .exists(_ <= height) && !blockchain.settings.functionalitySettings.preActivatedFeatures.contains(id)

            FeatureActivationStatus(
              id,
              BlockchainFeatures.feature(id).fold("Unknown feature")(_.description),
              status,
              (BlockchainFeatures.implemented.contains(id), voted) match {
                case (false, _) => NodeFeatureStatus.NotImplemented
                case (_, true)  => NodeFeatureStatus.Voted
                case _          => NodeFeatureStatus.Implemented
              },
              blockchain.featureActivationHeight(id),
              if (status == BlockchainFeatureStatus.Undefined) blockchain.featureVotes(height).get(id).orElse(Some(0)) else None
            )
          }
        )
      )
    )
  }
}
