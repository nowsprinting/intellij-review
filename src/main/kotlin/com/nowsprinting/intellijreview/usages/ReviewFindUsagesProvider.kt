package com.nowsprinting.intellijreview.usages

import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Processor
import com.nowsprinting.intellijreview.lexer.ReviewLexerAdapter
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewFindUsagesProvider : FindUsagesProvider {
    // 計画書は当初DefaultWordsScannerを想定していたが、実測で2つの問題を確認し自前実装に
    // 切り替えた(advisorレビューで指摘された懸念が実際に的中した)。
    // (1) DefaultWordsScannerの識別子境界分割は、ハイフン入りIDの完全一致ワードを
    //     検索インデックスに載せられない。
    // (2) 分割を一切せずトークン全体を1ワードにする案も試したが、"chapterId|id"の
    //     ような章跨ぎ記法では"id"だけの完全一致検索(ReferencesSearchは対象要素の
    //     名前=完全一致ワードでインデックスを引く)がヒットしなくなり、むしろ悪化した。
    // 最終的に、"|"(章跨ぎの区切り文字。このプロジェクト自身の文法上の意味を持つ既知の
    // 区切り)でのみ分割し、それ以外の文字(ハイフン・アンダースコア等)では分割しない
    // 自前のWordsScannerとした。
    override fun getWordsScanner(): WordsScanner = WordsScanner { fileText, processor ->
        val lexer = ReviewLexerAdapter()
        lexer.start(fileText)
        while (lexer.tokenType != null) {
            if (lexer.tokenType in INDEXED_TOKENS) {
                val tokenStart = lexer.tokenStart
                val tokenEnd = lexer.tokenEnd
                var segmentStart = tokenStart
                for (offset in tokenStart..tokenEnd) {
                    if (offset == tokenEnd || fileText[offset] == '|') {
                        if (offset > segmentStart) {
                            val occurrence = WordOccurrence(fileText, segmentStart, offset, WordOccurrence.Kind.CODE)
                            if (!processor.process(occurrence)) return@WordsScanner
                        }
                        segmentStart = offset + 1
                    }
                }
            }
            lexer.advance()
        }
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is ReviewBlockCommandMixin || psiElement is ReviewHeadingMixin || psiElement is PsiFile

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is ReviewBlockCommandMixin -> element.getCommandName()
        is ReviewHeadingMixin -> "heading"
        is PsiFile -> "file"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is ReviewBlockCommandMixin -> element.getIdArg() ?: ""
        is ReviewHeadingMixin -> element.getLabel() ?: ""
        is PsiFile -> element.name
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)

    companion object {
        // ID/ラベル/インライン内容を保持するトークンのみ索引化する(本文テキストは
        // 参照の対象にならないため対象外)。
        private val INDEXED_TOKENS =
            setOf(ReviewTypes.BLOCK_ARG_TEXT, ReviewTypes.HEADING_LABEL_ID, ReviewTypes.INLINE_CONTENT)
    }
}
