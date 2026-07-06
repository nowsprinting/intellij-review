package com.nowsprinting.intellijreview.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.nowsprinting.intellijreview.psi.ReviewTypes

abstract class ReviewBlockCommandMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    fun getCommandName(): String =
        node.findChildByType(ReviewTypes.BLOCK_COMMAND_NAME)?.text?.removePrefix("//") ?: ""

    // ID引数を取る命令(1個目の"[...]"がID)。それ以外の命令は常にnullを返す。
    fun getIdArg(): String? {
        if (getCommandName() !in ID_ARG_COMMANDS) return null

        val children = node.getChildren(null)
        val firstBracket = children.indexOfFirst { it.elementType == ReviewTypes.LBRACKET }
        if (firstBracket < 0) return null

        val text = StringBuilder()
        for (i in (firstBracket + 1) until children.size) {
            val child = children[i]
            if (child.elementType == ReviewTypes.RBRACKET) break
            if (child.elementType == ReviewTypes.BLOCK_ARG_TEXT) text.append(child.text)
        }
        return text.toString()
    }

    // リネーム(Shift+F6)用: 1個目の"[...]"内のBLOCK_ARG_TEXT葉。IDを含まない命令や
    // (通常あり得ない)複数葉に分かれたIDはnullを返す(単純なリネーム対象のみ扱う)。
    override fun getNameIdentifier(): PsiElement? {
        if (getCommandName() !in ID_ARG_COMMANDS) return null
        val children = node.getChildren(null)
        val firstBracket = children.indexOfFirst { it.elementType == ReviewTypes.LBRACKET }
        if (firstBracket < 0) return null
        for (i in (firstBracket + 1) until children.size) {
            val child = children[i]
            if (child.elementType == ReviewTypes.RBRACKET) break
            if (child.elementType == ReviewTypes.BLOCK_ARG_TEXT) return child.psi
        }
        return null
    }

    // PsiElement.getTextOffset()の既定実装(要素全体の開始位置)のままだと、
    // TargetElementUtilの宣言側キャレット解決(getNamedElement)が
    // 「親の getTextOffset() == キャレット位置の葉の開始位置」という一致判定に失敗し、
    // Shift+F6が宣言位置から効かなくなる。ID葉の開始位置を返すよう上書きする。
    override fun getTextOffset(): Int = nameIdentifier?.textRange?.startOffset ?: super.getTextOffset()

    override fun getName(): String? = getIdArg()

    override fun setName(name: String): PsiElement {
        val identifier = nameIdentifier ?: return this
        val range = TextRange(identifier.startOffsetInParent, identifier.startOffsetInParent + identifier.textLength)
        return ElementManipulators.getManipulator(this).handleContentChange(this, range, name) ?: this
    }

    companion object {
        private val ID_ARG_COMMANDS = setOf(
            "list", "listnum", "image", "indepimage", "numberlessimage", "graph",
            "table", "imgtable", "texequation", "footnote", "endnote", "bibpaper", "label"
        )
    }
}
