package com.aeneas.common

import com.google.common.primitives.UnsignedBytes
import com.aeneas.common.state.ByteStr

object ByteStrComparator {
  def compare(bs1: ByteStr, bs2: ByteStr): Int = UnsignedBytes.lexicographicalComparator().compare(bs1.arr, bs2.arr)
}
