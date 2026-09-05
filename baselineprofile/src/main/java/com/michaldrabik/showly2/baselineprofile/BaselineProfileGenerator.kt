package com.michaldrabik.showly2.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {

  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  @Test
  fun startup() =
    baselineProfileRule.collect(
      packageName = PACKAGE_NAME,
      includeInStartupProfile = true,
    ) {
      pressHome()
      startActivityAndWait()
      device.waitForIdle()
    }

  companion object {
    private const val PACKAGE_NAME = "com.michaldrabik.showly2"
  }
}
