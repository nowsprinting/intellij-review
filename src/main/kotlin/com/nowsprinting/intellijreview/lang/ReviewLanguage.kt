package com.nowsprinting.intellijreview.lang

import com.intellij.lang.Language

object ReviewLanguage : Language("ReVIEW") {
    private fun readResolve(): Any = ReviewLanguage
}
