package com.aeneas.consensus.nxt
import com.aeneas.common.state.ByteStr

case class NxtLikeConsensusBlockData(baseTarget: Long, generationSignature: ByteStr)
