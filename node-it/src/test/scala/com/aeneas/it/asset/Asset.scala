package com.aeneas.it.asset

case class Asset(assetType: String, name: String, description: String, quantity: Long, reissuable: Boolean, decimals: Byte, nonce: Long)
