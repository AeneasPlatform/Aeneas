package com.aeneas.state.diffs

import com.aeneas.state.Diff
import org.scalatest.matchers.{MatchResult, Matcher}

class DiffProduceError(errorMessage: String, requireFailed: Boolean) extends Matcher[Either[_, _]] {
  override def apply(ei: Either[_, _]): MatchResult = {
    ei match {
      case r @ Right(diff: Diff) =>
        diff.scriptResults.values.find(_.error.exists(_.text.contains(errorMessage))) match {
          case Some(_) => MatchResult(matches = true, "", "", Vector.empty)
          case None    => MatchResult(matches = false, "expecting Left(...{0}...) but got {1}", "got expected error", IndexedSeq(errorMessage, r))
        }

      case r @ Right(_) => MatchResult(matches = false, "expecting Left(...{0}...) but got {1}", "got expected error", IndexedSeq(errorMessage, r))

      case l @ Left(_) =>
        MatchResult(
          matches = !requireFailed && (l.toString contains errorMessage),
          "expecting Left(...{0}...) but got {1}",
          "got expected error",
          IndexedSeq(errorMessage, l)
        )
    }
  }
}
