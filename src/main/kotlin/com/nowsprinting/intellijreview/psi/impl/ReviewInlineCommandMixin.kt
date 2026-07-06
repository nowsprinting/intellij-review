package com.nowsprinting.intellijreview.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.reference.ReviewReferenceFactory

abstract class ReviewInlineCommandMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    fun getCommandName(): String = node.findChildByType(ReviewTypes.INLINE_NAME)?.text ?: ""

    override fun getReferences(): Array<PsiReference> = ReviewReferenceFactory.createReferences(this)

    // ブレース形式("{...}")のみ"\}"エスケープを解除する。$/|フェンス形式に
    // エスケープ機構はない(ReviewLexer.flexの設計どおり)。
    fun getContentText(): String {
        val children = node.getChildren(null)
        val isBraceForm = children.any { it.elementType == ReviewTypes.INLINE_LBRACE }
        val raw = children
            .filter { it.elementType == ReviewTypes.INLINE_CONTENT }
            .joinToString("") { it.text }
        return if (isBraceForm) raw.replace("\\}", "}") else raw
    }
}
