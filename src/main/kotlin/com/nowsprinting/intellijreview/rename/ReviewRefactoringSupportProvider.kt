package com.nowsprinting.intellijreview.rename

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewRefactoringSupportProvider : RefactoringSupportProvider() {
    // ラベルの無い見出しはダイアログでリネームさせる。インプレースリネームは編集範囲を
    // getNameIdentifier()(ラベル葉)から取るため、キャプションの見出しでは編集範囲が無い。
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        if (element is ReviewHeadingMixin) return element.getLabel() != null
        return super.isMemberInplaceRenameAvailable(element, context)
    }
}
