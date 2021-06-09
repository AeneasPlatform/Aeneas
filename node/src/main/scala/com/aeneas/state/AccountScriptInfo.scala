package com.aeneas.state

import com.aeneas.account.PublicKey
import com.aeneas.lang.script.Script

case class AccountScriptInfo(
    publicKey: PublicKey,
    script: Script,
    verifierComplexity: Long,
    complexitiesByEstimator: Map[Int, Map[String, Long]] = Map.empty
)
