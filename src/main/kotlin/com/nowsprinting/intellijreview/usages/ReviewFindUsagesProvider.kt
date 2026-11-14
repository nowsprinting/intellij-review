package com.nowsprinting.intellijreview.usages

import com.intellij.lang.cacheBuilder.VersionedWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Processor
import com.nowsprinting.intellijreview.lexer.ReviewLexerAdapter
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewFindUsagesProvider : FindUsagesProvider {
    // 検索側(PsiSearchHelperImpl)は検索語をStringUtil.getWordsIn(isJavaIdentifierPart境界)で
    // 分割し、全ワードを含むファイルをインデックスで引く。ハイフン入りIDやスペース入り
    // キャプションをトークン丸ごと1ワードで登録すると他ファイルの被リンクを取りこぼすため、
    // 同じ境界で分割して登録する。DefaultWordsScannerを使わないのは、本文テキストまで
    // 索引化してしまうため(INDEXED_TOKENSのみに絞る)。
    // 素のWordsScannerだとIdIndexのバージョンが固定(-1)になり、分割規則を変えても既存の
    // インデックスが再構築されず、開いていないファイルの被リンクを取りこぼす。分割規則を
    // 変えたらgetVersion()を上げること。
    override fun getWordsScanner(): WordsScanner = object : VersionedWordsScanner() {
        override fun getVersion(): Int = 2

        override fun processWords(fileText: CharSequence, processor: Processor<in WordOccurrence>) {
            val lexer = ReviewLexerAdapter()
            lexer.start(fileText)
            while (lexer.tokenType != null) {
                if (lexer.tokenType in INDEXED_TOKENS) {
                    val tokenStart = lexer.tokenStart
                    for (word in StringUtil.getWordIndicesIn(fileText.substring(tokenStart, lexer.tokenEnd))) {
                        val occurrence = WordOccurrence(
                            fileText, tokenStart + word.startOffset, tokenStart + word.endOffset, WordOccurrence.Kind.CODE
                        )
                        if (!processor.process(occurrence)) return
                    }
                }
                lexer.advance()
            }
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
        is ReviewHeadingMixin -> element.name
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
