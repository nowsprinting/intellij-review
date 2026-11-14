package com.nowsprinting.intellijreview.reference

import com.intellij.codeInsight.hint.DefaultImplementationTextSelectioner
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

// Quick Definition(Cmd+Y)で見出しの定義を表示する範囲。見出しのPSIは見出し行のみで
// セクション本文を子に持たないため、既定の範囲では見出し行しか表示されない。
// セクションの終わりはReviewHeadingTreeBuilderと同じく、同位以上の見出しか"[/column]"の手前とする。
class ReviewImplementationTextSelectioner : DefaultImplementationTextSelectioner() {
    override fun getTextEndOffset(element: PsiElement): Int {
        if (element !is ReviewHeadingMixin) return super.getTextEndOffset(element)
        val next = PsiTreeUtil.findChildrenOfType(element.containingFile, ReviewHeadingMixin::class.java)
            .sortedBy { it.textRange.startOffset }
            .firstOrNull {
                it.textRange.startOffset > element.textRange.startOffset &&
                    (it.isColumnClose() || it.getLevel() <= element.getLevel())
            }
        return next?.textRange?.startOffset ?: element.containingFile.textLength
    }
}
