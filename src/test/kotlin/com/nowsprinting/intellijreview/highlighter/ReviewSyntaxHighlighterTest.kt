package com.nowsprinting.intellijreview.highlighter

import com.nowsprinting.intellijreview.psi.ReviewTypes
import junit.framework.TestCase

class ReviewSyntaxHighlighterTest : TestCase() {
    private val highlighter = ReviewSyntaxHighlighter()

    fun test見出しマーカーはHEADINGキーを持つ() {
        assertEquals(
            listOf(ReviewSyntaxHighlighter.HEADING),
            highlighter.getTokenHighlights(ReviewTypes.HEADING_MARKER).toList()
        )
    }

    fun test見出しラベルの開始終了とIDは全てHEADING_LABELキーを持つ() {
        val key = ReviewSyntaxHighlighter.HEADING_LABEL
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.HEADING_LABEL_OPEN).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.HEADING_LABEL_ID).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.HEADING_LABEL_CLOSE).toList())
    }

    fun testブロック命令名はBLOCK_COMMANDキーを持つ() {
        assertEquals(
            listOf(ReviewSyntaxHighlighter.BLOCK_COMMAND),
            highlighter.getTokenHighlights(ReviewTypes.BLOCK_COMMAND_NAME).toList()
        )
    }

    fun testインライン命令の開始名閉じタグは全てINLINE_COMMANDキーを持つ() {
        val key = ReviewSyntaxHighlighter.INLINE_COMMAND
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_START).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_NAME).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_TAG_CLOSE).toList())
    }

    fun testインライン内容とブレースフェンスは全てINLINE_CONTENTキーを持つ() {
        val key = ReviewSyntaxHighlighter.INLINE_CONTENT
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_LBRACE).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_RBRACE).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_FENCE).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.INLINE_CONTENT).toList())
    }

    fun test箇条書き3種のマーカーは全てLIST_MARKERキーを持つ() {
        val key = ReviewSyntaxHighlighter.LIST_MARKER
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.UL_MARKER).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.OL_MARKER).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.DL_MARKER).toList())
    }

    fun testコメントはCOMMENTキーを持つ() {
        assertEquals(
            listOf(ReviewSyntaxHighlighter.COMMENT),
            highlighter.getTokenHighlights(ReviewTypes.LINE_COMMENT).toList()
        )
    }

    fun testブロックの角括弧と波括弧終端子は全てBLOCK_BRACKETSキーを持つ() {
        val key = ReviewSyntaxHighlighter.BLOCK_BRACKETS
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.LBRACKET).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.RBRACKET).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.BLOCK_BODY_LBRACE).toList())
        assertEquals(listOf(key), highlighter.getTokenHighlights(ReviewTypes.BLOCK_END).toList())
    }

    fun test普通のテキストとEOLはハイライトキーを持たない() {
        assertTrue(highlighter.getTokenHighlights(ReviewTypes.TEXT).isEmpty())
        assertTrue(highlighter.getTokenHighlights(ReviewTypes.EOL).isEmpty())
    }
}
