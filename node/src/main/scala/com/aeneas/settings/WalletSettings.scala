package com.aeneas.settings

import java.io.File

import com.aeneas.common.state.ByteStr

case class WalletSettings(file: Option[File], password: Option[String], seed: Option[ByteStr])
