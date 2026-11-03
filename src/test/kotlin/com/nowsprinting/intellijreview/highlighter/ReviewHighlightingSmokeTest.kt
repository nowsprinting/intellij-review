package com.nowsprinting.intellijreview.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.lang.ReviewLanguage

class ReviewHighlightingSmokeTest : BasePlatformTestCase() {
    fun test登録済みシンタックスハイライタで見出しとインライン命令が色付けされる() {
        myFixture.configureByText(
            "sample.re",
            "= Chapter title\nHello @<b>{bold} text.\n#@# comment\n"
        )
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            ReviewLanguage,
            myFixture.project,
            myFixture.file.virtualFile
        )

        val lexer = highlighter.highlightingLexer
        lexer.start(myFixture.file.text)
        val seenKeys = mutableSetOf<TextAttributesKey>()
        while (lexer.tokenType != null) {
            seenKeys.addAll(highlighter.getTokenHighlights(lexer.tokenType))
            lexer.advance()
        }

        assertTrue(seenKeys.contains(ReviewSyntaxHighlighter.HEADING))
        assertTrue(seenKeys.contains(ReviewSyntaxHighlighter.INLINE_COMMAND))
        assertTrue(seenKeys.contains(ReviewSyntaxHighlighter.INLINE_CONTENT))
        assertTrue(seenKeys.contains(ReviewSyntaxHighlighter.COMMENT))
    }
}
