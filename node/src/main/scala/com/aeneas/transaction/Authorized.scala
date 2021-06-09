package com.aeneas.transaction
import com.aeneas.account.PublicKey

trait Authorized {
  val sender: PublicKey
}
