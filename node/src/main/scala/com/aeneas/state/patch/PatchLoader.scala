package com.aeneas.state.patch

import com.aeneas.account.AddressScheme
import com.aeneas.utils.ScorexLogging
import play.api.libs.json.{Json, Reads}

object PatchLoader extends ScorexLogging {
  def read[T: Reads](name: AnyRef): T = {
    val inputStream = Thread.currentThread().getContextClassLoader.getResourceAsStream(s"patches/$name-${AddressScheme.current.chainId.toChar}.json")
    Json.parse(inputStream).as[T]
  }
}
