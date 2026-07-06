package com.nowsprinting.intellijreview.psi

import com.intellij.psi.tree.IElementType
import com.nowsprinting.intellijreview.lang.ReviewLanguage

class ReviewTokenType(debugName: String) : IElementType(debugName, ReviewLanguage) {
    override fun toString(): String = "ReviewTokenType.${super.toString()}"
}
