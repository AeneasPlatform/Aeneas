package com.aeneas.transaction.validation

import scala.annotation.implicitNotFound

@implicitNotFound("No impllicit transaction validator found for transaction ${T}")
trait TxValidator[T] {
  def validate(tx: T): ValidatedV[T]
}
