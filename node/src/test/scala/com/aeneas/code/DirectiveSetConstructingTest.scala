package com.aeneas.code

import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values._
import org.scalatest.{Matchers, PropSpec}

class DirectiveSetConstructingTest extends PropSpec with Matchers {
  property("DirectiveSet should be successfully constructed with (V3, Account, Contract) params") {
    DirectiveSet(V3, Account, DApp) shouldBe DirectiveSet(V3, Account, DApp)
  }

  property("DirectiveSet should be successfully constructed with (<any>, <any>, Expression) params") {
    DirectiveSet(V1, Account, Expression) shouldBe DirectiveSet(V1, Account, Expression)
    DirectiveSet(V2, Asset, Expression) shouldBe DirectiveSet(V2, Asset, Expression)
    DirectiveSet(V3, Asset, Expression) shouldBe DirectiveSet(V3, Asset, Expression)
  }
}
