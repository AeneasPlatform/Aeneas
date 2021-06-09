package com.aeneas.lang.v1.traits.domain
import com.aeneas.common.state.ByteStr

case class BlockInfo(timestamp: Long,
                     height: Int,
                     baseTarget: Long,
                     generationSignature: ByteStr,
                     generator: ByteStr,
                     generatorPublicKey: ByteStr,
                     vrf: Option[ByteStr])
