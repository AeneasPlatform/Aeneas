package com.aeneas.api.http

import java.security.SecureRandom

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Route
import com.aeneas.api.http.ApiError.{ScriptCompilerError, TooBigArrayAllocation}
import com.aeneas.api.http.requests.ScriptWithImportsRequest
import com.aeneas.common.utils._
import com.aeneas.crypto
import com.aeneas.lang.Global
import com.aeneas.lang.script.Script
import com.aeneas.lang.script.Script.ComplexityInfo
import com.aeneas.lang.v1.estimator.ScriptEstimator
import com.aeneas.settings.RestAPISettings
import com.aeneas.state.diffs.FeeValidation
import com.aeneas.transaction.smart.script.ScriptCompiler
import com.aeneas.utils.Time
import com.aeneas.state.Blockchain
import com.aeneas.features.BlockchainFeatures
import com.aeneas.lang.directives.values._
import monix.execution.Scheduler
import play.api.libs.json._

case class UtilsApiRoute(
    timeService: Time,
    settings: RestAPISettings,
    estimator: ScriptEstimator,
    limitedScheduler: Scheduler,
    blockchain: Blockchain
) extends ApiRoute
    with AuthRoute
    with TimeLimitedRoute {

  import UtilsApiRoute._

  private def seed(length: Int) = {
    val seed = new Array[Byte](length)
    new SecureRandom().nextBytes(seed) //seed mutated here!
    Json.obj("seed" -> Base58.encode(seed))
  }

  override val route: Route = pathPrefix("utils") {
    decompile ~ compile ~ compileCode ~ compileWithImports ~ scriptMeta ~ estimate ~ time ~ seedRoute ~ length ~ hashFast ~ hashSecure ~ transactionSerialize
  }

  def decompile: Route = path("script" / "decompile") {
    import play.api.libs.json.Json.toJsFieldJsValueWrapper

    (post & entity(as[String])) { code =>
      Script.fromBase64String(code.trim) match {
        case Left(err) => complete(err)
        case Right(script) =>
          executeLimited(Script.decompile(script)) {
            case (scriptText, meta) =>
              val directives: List[(String, JsValue)] = meta.map {
                case (k, v) =>
                  (k, v match {
                    case n: Number => JsNumber(BigDecimal(n.toString))
                    case s         => JsString(s.toString)
                  })
              }
              val result  = directives ::: "script" -> JsString(scriptText) :: Nil
              val wrapped = result.map { case (k, v) => (k, toJsFieldJsValueWrapper(v)) }
              complete(
                Json.obj(wrapped: _*)
              )
          }
      }
    }
  }

  // Deprecated
  def compile: Route = path("script" / "compile") {
    (post & entity(as[String])) { code =>
      parameter("assetScript".as[Boolean] ? false) { isAssetScript =>
        executeLimited(ScriptCompiler(code, isAssetScript, estimator)) { result =>
          complete(
            result.fold(
              e => ScriptCompilerError(e), {
                case (script, complexity) =>
                  Json.obj(
                    "script"     -> script.bytes().base64,
                    "complexity" -> complexity,
                    "extraFee"   -> FeeValidation.ScriptExtraFee
                  )
              }
            )
          )
        }
      }
    }
  }

  def compileCode: Route = path("script" / "compileCode") {
    (post & entity(as[String])) { code =>
      def stdLib: StdLibVersion = if(blockchain.isFeatureActivated(BlockchainFeatures.Ride4DApps, blockchain.height)) { V4 } else { StdLibVersion.VersionDic.default }
      executeLimited(ScriptCompiler.compileAndEstimateCallables(code, estimator, defaultStdLib = stdLib)) { result =>
        complete(
          result
            .fold(
              e => ScriptCompilerError(e), {
                case (script, ComplexityInfo(verifierComplexity, callableComplexities, maxComplexity)) =>
                  Json.obj(
                    "script"               -> script.bytes().base64,
                    "complexity"           -> maxComplexity,
                    "verifierComplexity"   -> verifierComplexity,
                    "callableComplexities" -> callableComplexities,
                    "extraFee"             -> FeeValidation.ScriptExtraFee
                  )
              }
            )
        )

      }

    }
  }

  def compileWithImports: Route = path("script" / "compileWithImports") {
    import ScriptWithImportsRequest._
    (post & entity(as[ScriptWithImportsRequest])) { req =>
      executeLimited(ScriptCompiler.compile(req.script, estimator, req.imports)) { result =>
        complete(
          result
            .fold(
              e => ScriptCompilerError(e), {
                case (script, complexity) =>
                  Json.obj(
                    "script"     -> script.bytes().base64,
                    "complexity" -> complexity,
                    "extraFee"   -> FeeValidation.ScriptExtraFee
                  )
              }
            )
        )
      }
    }
  }

  def estimate: Route = path("script" / "estimate") {
    (post & entity(as[String])) { code =>
      executeLimited(
        Script
          .fromBase64String(code)
          .left
          .map(_.m)
          .flatMap { script =>
            Script.complexityInfo(script, estimator, useContractVerifierLimit = false).map((script, _))
          }
      ) { result =>
        complete(
          result.fold(
            e => ScriptCompilerError(e), {
              case (script, ComplexityInfo(verifierComplexity, callableComplexities, maxComplexity)) =>
                Json.obj(
                  "script"               -> code,
                  "scriptText"           -> script.expr.toString, // [WAIT] Script.decompile(script),
                  "complexity"           -> maxComplexity,
                  "verifierComplexity"   -> verifierComplexity,
                  "callableComplexities" -> callableComplexities,
                  "extraFee"             -> FeeValidation.ScriptExtraFee
                )
            }
          )
        )
      }
    }
  }

  def scriptMeta: Route = path("script" / "meta") {
    (post & entity(as[String])) { code =>
      val result: ToResponseMarshallable = Global
        .dAppFuncTypes(code) // Does not estimate complexity, therefore it should not hang
        .fold(e => e, r => r)
      complete(result)
    }
  }

  def time: Route = (path("time") & get) {
    complete(Json.obj("system" -> System.currentTimeMillis(), "NTP" -> timeService.correctedTime()))
  }

  def seedRoute: Route = (path("seed") & get) {
    complete(seed(DefaultSeedSize))
  }

  def length: Route = (path("seed" / IntNumber) & get) { length =>
    if (length <= MaxSeedSize) complete(seed(length))
    else complete(TooBigArrayAllocation)
  }

  def hashSecure: Route = (path("hash" / "secure") & post) {
    entity(as[String]) { message =>
      complete(Json.obj("message" -> message, "hash" -> Base58.encode(crypto.secureHash(message))))
    }
  }

  def hashFast: Route = (path("hash" / "fast") & post) {
    entity(as[String]) { message =>
      complete(Json.obj("message" -> message, "hash" -> Base58.encode(crypto.fastHash(message))))
    }
  }

  def transactionSerialize: Route =
    path("transactionSerialize")(jsonPost[JsObject] { jsv =>
      parseOrCreateTransaction(jsv)(tx => Json.obj("bytes" -> tx.bodyBytes().map(_.toInt & 0xff)))
    })
}

object UtilsApiRoute {
  val MaxSeedSize     = 1024
  val DefaultSeedSize = 32
}
