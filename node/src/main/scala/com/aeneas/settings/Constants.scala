package com.aeneas.settings

import com.aeneas.Version
import com.aeneas.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val ApplicationName = "waves"
  val AgentName       = s"Aeneas v${Version.VersionString}"

  val UnitsInAsh = 100_000_000L
  val TotalAsh  = 86_608_965L
  val AshPrecision = 8;

  lazy val TransactionNames: Map[Byte, String] =
    Map(
      (1: Byte) -> "GenesisTransaction",
      (2: Byte) -> "PaymentTransaction",
      (3: Byte) -> "IssueTransaction",
      (4: Byte) -> "TransferTransaction",
      (5: Byte) -> "ReissueTransaction",
      (6: Byte) -> "BurnTransaction",
      (7: Byte) -> "ExchangeTransaction",
      (8: Byte) -> "LeaseTransaction",
      (9: Byte) -> "LeaseCancelTransaction",
      (10: Byte) -> "CreateAliasTransaction",
      (11: Byte) -> "MassTransferTransaction",
      (12: Byte) -> "DataTransaction",
      (13: Byte) -> "SetScriptTransaction",
      (14: Byte) -> "SponsorFeeTransaction",
      (15: Byte) -> "SetAssetScriptTransaction",
      (16: Byte) -> "InvokeScriptTransaction",
      (17: Byte) -> "UpdateAssetInfoTransaction",
      (18: Byte) -> "ContinuationTransaction"
    )
}
