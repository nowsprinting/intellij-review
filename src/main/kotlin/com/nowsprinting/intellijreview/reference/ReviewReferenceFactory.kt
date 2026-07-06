package com.nowsprinting.intellijreview.reference

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.nowsprinting.intellijreview.completion.ReviewCommandCatalog
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

object ReviewReferenceFactory {

    fun createReferences(inlineCommand: ReviewInlineCommandMixin): Array<PsiReference> {
        val commandName = inlineCommand.getCommandName()
        val kind = ReviewCommandCatalog.INLINE_REFERENCE_KINDS[commandName] ?: return PsiReference.EMPTY_ARRAY

        val node = inlineCommand.node
        val contentChildren = node.getChildren(null).filter { it.elementType == ReviewTypes.INLINE_CONTENT }
        if (contentChildren.isEmpty()) return PsiReference.EMPTY_ARRAY

        val baseOffset = node.startOffset
        // "|"はフェンス形式($.../|...|)の区切り文字自身のため、エスケープ機構を持つ
        // ブレース形式含め、レクサはINLINE_CONTENT内に生テキストをそのまま渡す。ここでは
        // アンエスケープ前の生テキスト上でオフセット計算するため、getContentText()は使わない。
        val rawText = contentChildren.joinToString("") { it.text }

        if (kind == ReviewDeclarationKind.CHAPTER) {
            val range = rangeFor(contentChildren, baseOffset, 0, rawText.length)
            return arrayOf(ReviewChapterReference(inlineCommand, range, rawText))
        }

        if (commandName in ReviewCommandCatalog.GLOBAL_LOOKUP_COMMANDS) {
            val range = rangeFor(contentChildren, baseOffset, 0, rawText.length)
            return arrayOf(ReviewBibReference(inlineCommand, range, rawText))
        }

        // heading系(hd/secref/sec/sectitle)は"|"が章跨ぎかネストパスかを字句だけでは
        // 判別できないため、ReviewBlockIdReferenceの機械的な最初の"|"分割は使えない
        // (ReviewHeadingReferenceのコメント参照)。
        if (kind == ReviewDeclarationKind.HEADING) {
            val range = rangeFor(contentChildren, baseOffset, 0, rawText.length)
            return arrayOf(ReviewHeadingReference(inlineCommand, range, rawText, kind))
        }

        val pipeIndex = rawText.indexOf('|')
        if (pipeIndex >= 0 && commandName !in ReviewCommandCatalog.SAME_CHAPTER_ONLY_COMMANDS) {
            val chapterId = rawText.substring(0, pipeIndex)
            val id = rawText.substring(pipeIndex + 1)
            val chapterRange = rangeFor(contentChildren, baseOffset, 0, pipeIndex)
            val idRange = rangeFor(contentChildren, baseOffset, pipeIndex + 1, rawText.length)
            return arrayOf(
                ReviewChapterReference(inlineCommand, chapterRange, chapterId),
                ReviewBlockIdReference(inlineCommand, idRange, id, kind, chapterId),
            )
        }

        val range = rangeFor(contentChildren, baseOffset, 0, rawText.length)
        return arrayOf(ReviewBlockIdReference(inlineCommand, range, rawText, kind, chapterId = null))
    }

    // 生テキスト連結上の[from, to)を、複数に分かれうるINLINE_CONTENT子要素をまたいで
    // inline_command要素基準のローカルTextRangeへ変換する。
    private fun rangeFor(children: List<ASTNode>, baseOffset: Int, from: Int, to: Int): TextRange {
        var consumed = 0
        var startOffset = -1
        var endOffset = -1
        for (child in children) {
            val childLocalStart = child.startOffset - baseOffset
            val len = child.textLength
            if (startOffset == -1 && from <= consumed + len) startOffset = childLocalStart + (from - consumed)
            if (endOffset == -1 && to <= consumed + len) endOffset = childLocalStart + (to - consumed)
            consumed += len
            if (startOffset != -1 && endOffset != -1) break
        }
        return TextRange(startOffset, endOffset)
    }
}
