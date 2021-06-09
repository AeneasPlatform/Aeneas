package com.aeneas.db
import com.typesafe.config.ConfigFactory
import com.aeneas.settings.WavesSettings

trait DBCacheSettings {
  lazy val dbSettings = WavesSettings.fromRootConfig(ConfigFactory.load()).dbSettings
  lazy val maxCacheSize: Int = dbSettings.maxCacheSize
}
