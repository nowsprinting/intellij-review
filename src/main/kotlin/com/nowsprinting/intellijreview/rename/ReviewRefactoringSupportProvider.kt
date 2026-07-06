package com.nowsprinting.intellijreview.rename

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewRefactoringSupportProvider : RefactoringSupportProvider() {
    // ラベルの無い見出し(ReviewHeadingMixin.getNameIdentifier()がnull)はインプレースリネームも
    // 拒否する。通常のリネームアクションはPsiNameIdentifierOwner.getNameIdentifier()==nullで
    // 既に拒否されるが、F2のインプレースリネームは別経路のため明示しておく。
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        if (element is ReviewHeadingMixin) return element.getLabel() != null
        return super.isMemberInplaceRenameAvailable(element, context)
    }
}
