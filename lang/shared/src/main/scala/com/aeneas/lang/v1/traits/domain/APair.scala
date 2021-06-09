package com.aeneas.lang.v1.traits.domain

import com.aeneas.common.state.ByteStr

case class APair(amountAsset: Option[ByteStr], priceAsset: Option[ByteStr])
