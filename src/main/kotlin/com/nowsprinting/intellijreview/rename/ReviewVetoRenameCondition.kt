package com.nowsprinting.intellijreview.rename

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

// ReviewHeadingMixin.setName()が書き換えられないキャプションの見出しを、参照を書き換える前に拒否する。
// setName()の例外だけに頼ると、プラットフォームは参照を先に書き換えてから失敗する。
class ReviewVetoRenameCondition : Condition<PsiElement> {
    override fun value(element: PsiElement): Boolean = element is ReviewHeadingMixin && element.getLabel() == null && !element.hasPlainCaption()
}
