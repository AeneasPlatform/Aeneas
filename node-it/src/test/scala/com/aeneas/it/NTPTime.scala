package com.aeneas.it

import com.aeneas.utils.NTP
import org.scalatest.{BeforeAndAfterAll, Suite}

trait NTPTime extends BeforeAndAfterAll { _: Suite =>
  protected val ntpTime = new NTP("pool.ntp.org")

  override protected def afterAll(): Unit = {
    super.afterAll()
    ntpTime.close()
  }
}
