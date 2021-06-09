package com.aeneas.transaction

import cats.data.ValidatedNel
import com.aeneas.lang.ValidationError

package object validation {
  type ValidatedV[A] = ValidatedNel[ValidationError, A]
  type ValidatedNV   = ValidatedV[Unit]
}
