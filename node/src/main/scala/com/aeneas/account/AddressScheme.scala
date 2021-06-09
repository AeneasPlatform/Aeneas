package com.aeneas.account

import java.nio.charset.Charset

import com.aeneas.common.state.ByteStr

abstract class AddressScheme {
  val chainId: Byte
  override def toString: String = s"AddressScheme(${chainId})"
}

object AddressScheme {
  @volatile var current: AddressScheme = DefaultAddressScheme
}

object DefaultAddressScheme extends AddressScheme {
  val chainId: Byte = 'A'.toByte//'Æ'.toByte
}
