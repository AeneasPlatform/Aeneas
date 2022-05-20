package com.aeneas.api.http.requests

import com.aeneas.transaction.smart.SetScriptTransaction
import play.api.libs.json.{Format, JsNumber, JsObject, Json}

case class SetScriptRequest(version: Option[Byte], sender: String, script: Option[String], fee: Long, timestamp: Option[Long] = None) {}

object SetScriptRequest {
  implicit val jsonFormat: Format[SetScriptRequest] = Json.format
  implicit class SetScriptRequestExt(val self: SetScriptRequest) extends AnyVal {
    def toJsObject: JsObject = Json.toJson(self).as[JsObject] + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt))
  }
}
