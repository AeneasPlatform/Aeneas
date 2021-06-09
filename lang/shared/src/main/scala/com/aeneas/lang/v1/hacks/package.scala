package com.aeneas.lang

import com.aeneas.lang.v1.BaseGlobal

package object hacks {
  private[lang] val Global: BaseGlobal = com.aeneas.lang.Global // Hack for IDEA
}
