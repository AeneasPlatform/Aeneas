package com.aeneas.state.patch

import com.aeneas.state.{Blockchain, Diff}

trait DiffPatchFactory {
  def isApplicable(b: Blockchain): Boolean = b.height == this.height
  def height: Int
  def apply(): Diff
}
