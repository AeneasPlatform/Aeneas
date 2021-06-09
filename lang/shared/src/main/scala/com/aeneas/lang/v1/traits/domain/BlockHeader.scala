package com.aeneas.lang.v1.traits.domain

import com.aeneas.common.state.ByteStr

final case class BlockHeader(timestamp: Long,
                             version: Long,
                             reference: ByteStr,
                             generator: ByteStr,
                             generatorPublicKey: ByteStr,
                             signature: ByteStr,
                             baseTarget: Long,
                             generationSignature: ByteStr,
                             transactionCount: Long,
                             featureVotes: Seq[Long])
