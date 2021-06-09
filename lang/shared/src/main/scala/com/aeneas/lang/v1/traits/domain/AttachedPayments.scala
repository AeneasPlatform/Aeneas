package com.aeneas.lang.v1.traits.domain

import com.aeneas.common.state.ByteStr
import com.aeneas.lang.directives.values.{StdLibVersion, V4}

sealed trait AttachedPayments

object AttachedPayments {
  type Payment = (Long, Option[ByteStr])

  case class Single(p: Option[Payment]) extends AttachedPayments
  case class Multi(p: Seq[Payment]) extends AttachedPayments

  val MultiPaymentSupportedVersion: StdLibVersion = V4

  implicit class StdLibVersionMultiPaymentOps(version: StdLibVersion) {
    def supportsMultiPayment: Boolean = version >= MultiPaymentSupportedVersion
  }
}