package com.aeneas.lang.v1.traits.domain

import com.aeneas.common.state.ByteStr

sealed trait Recipient
object Recipient {
  case class Address(bytes: ByteStr) extends Recipient
  case class Alias(name: String)     extends Recipient
}
