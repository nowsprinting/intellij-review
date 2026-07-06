package com.nowsprinting.intellijreview.lexer

import com.intellij.testFramework.LexerTestCase

class ReviewLexerAdapterTest : LexerTestCase() {
    override fun createLexer() = ReviewLexerAdapter()
    override fun getDirPath(): String = ""

    fun test見出し行とテキスト行とEOLをレクスできる() {
        val text = "=Chapter 1\nHello\n"
        doTest(
            text,
            """
            ReviewTokenType.HEADING_MARKER ('=')
            ReviewTokenType.TEXT ('Chapter 1')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.TEXT ('Hello')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test見出しの各レベルをレクスできる() {
        val text = """
            = Level 1
            == Level 2
            === Level 3
            ==== Level 4
            ===== Level 5
            ====== Level 6
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            ReviewTokenType.HEADING_MARKER ('=')
            ReviewTokenType.TEXT (' Level 1')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.HEADING_MARKER ('==')
            ReviewTokenType.TEXT (' Level 2')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.HEADING_MARKER ('===')
            ReviewTokenType.TEXT (' Level 3')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.HEADING_MARKER ('====')
            ReviewTokenType.TEXT (' Level 4')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.HEADING_MARKER ('=====')
            ReviewTokenType.TEXT (' Level 5')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.HEADING_MARKER ('======')
            ReviewTokenType.TEXT (' Level 6')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test見出しのオプションとラベルをレクスできる() {
        val text = "=[column]{chap1} Introduction\n"
        doTest(
            text,
            """
            ReviewTokenType.HEADING_MARKER ('=')
            ReviewTokenType.LBRACKET ('[')
            ReviewTokenType.HEADING_OPTION ('column')
            ReviewTokenType.RBRACKET (']')
            ReviewTokenType.HEADING_LABEL_OPEN ('{')
            ReviewTokenType.HEADING_LABEL_ID ('chap1')
            ReviewTokenType.HEADING_LABEL_CLOSE ('}')
            ReviewTokenType.TEXT (' Introduction')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testブロック命令の1行形式をレクスできる() {
        val text = "//noheader\n//list[id1]\n"
        doTest(
            text,
            """
            ReviewTokenType.BLOCK_COMMAND_NAME ('//noheader')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.BLOCK_COMMAND_NAME ('//list')
            ReviewTokenType.LBRACKET ('[')
            ReviewTokenType.BLOCK_ARG_TEXT ('id1')
            ReviewTokenType.RBRACKET (']')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testブロック命令の複数行形式と終端子をレクスできる() {
        val text = """
            //list[id1][caption1]{
            line1
            line2
            //}
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            ReviewTokenType.BLOCK_COMMAND_NAME ('//list')
            ReviewTokenType.LBRACKET ('[')
            ReviewTokenType.BLOCK_ARG_TEXT ('id1')
            ReviewTokenType.RBRACKET (']')
            ReviewTokenType.LBRACKET ('[')
            ReviewTokenType.BLOCK_ARG_TEXT ('caption1')
            ReviewTokenType.RBRACKET (']')
            ReviewTokenType.BLOCK_BODY_LBRACE ('{')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.RAW_TEXT ('line1')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.RAW_TEXT ('line2')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.BLOCK_END ('//}')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testブロック本文verbatim内のインライン命令をレクスできる() {
        val text = """
            //list[id1]{
            plain text @<b>{bold} more text
            //}
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            ReviewTokenType.BLOCK_COMMAND_NAME ('//list')
            ReviewTokenType.LBRACKET ('[')
            ReviewTokenType.BLOCK_ARG_TEXT ('id1')
            ReviewTokenType.RBRACKET (']')
            ReviewTokenType.BLOCK_BODY_LBRACE ('{')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.RAW_TEXT ('plain text ')
            ReviewTokenType.INLINE_START ('@<')
            ReviewTokenType.INLINE_NAME ('b')
            ReviewTokenType.INLINE_TAG_CLOSE ('>')
            ReviewTokenType.INLINE_LBRACE ('{')
            ReviewTokenType.INLINE_CONTENT ('bold')
            ReviewTokenType.INLINE_RBRACE ('}')
            ReviewTokenType.RAW_TEXT (' more text')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.BLOCK_END ('//}')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testブロック本文の終端子は非ゼロオフセットからのリスタートでも認識される() {
        // ブロックの直前に段落を置き、"//}"(行頭アンカー)より手前にYYINITIAL(state=0)の
        // リスタート地点を作ることで、行頭判定がバッファ先頭以外からの再開でも
        // 正しく機能することをcheckCorrectRestartで検証する。
        val text = """
            intro paragraph
            //list[id]{
            body line
            //}
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            ReviewTokenType.TEXT ('intro paragraph')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.BLOCK_COMMAND_NAME ('//list')
            ReviewTokenType.LBRACKET ('[')
            ReviewTokenType.BLOCK_ARG_TEXT ('id')
            ReviewTokenType.RBRACKET (']')
            ReviewTokenType.BLOCK_BODY_LBRACE ('{')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.RAW_TEXT ('body line')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.BLOCK_END ('//}')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testインラインのエスケープとフェンスをレクスできる() {
        val text = "@<b>{esc\\}aped} @<m>\$a+b\$ @<m>|pipe|\n"
        doTest(
            text,
            """
            ReviewTokenType.INLINE_START ('@<')
            ReviewTokenType.INLINE_NAME ('b')
            ReviewTokenType.INLINE_TAG_CLOSE ('>')
            ReviewTokenType.INLINE_LBRACE ('{')
            ReviewTokenType.INLINE_CONTENT ('esc')
            ReviewTokenType.INLINE_CONTENT ('\}')
            ReviewTokenType.INLINE_CONTENT ('aped')
            ReviewTokenType.INLINE_RBRACE ('}')
            ReviewTokenType.TEXT (' ')
            ReviewTokenType.INLINE_START ('@<')
            ReviewTokenType.INLINE_NAME ('m')
            ReviewTokenType.INLINE_TAG_CLOSE ('>')
            ReviewTokenType.INLINE_FENCE ('${'$'}')
            ReviewTokenType.INLINE_CONTENT ('a+b')
            ReviewTokenType.INLINE_FENCE ('${'$'}')
            ReviewTokenType.TEXT (' ')
            ReviewTokenType.INLINE_START ('@<')
            ReviewTokenType.INLINE_NAME ('m')
            ReviewTokenType.INLINE_TAG_CLOSE ('>')
            ReviewTokenType.INLINE_FENCE ('|')
            ReviewTokenType.INLINE_CONTENT ('pipe')
            ReviewTokenType.INLINE_FENCE ('|')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test箇条書き3種をレクスできる() {
        val text = """
            * item1
            ** item2
            1. first
            : term
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            ReviewTokenType.UL_MARKER ('* ')
            ReviewTokenType.TEXT ('item1')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.UL_MARKER ('** ')
            ReviewTokenType.TEXT ('item2')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.OL_MARKER ('1. ')
            ReviewTokenType.TEXT ('first')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.DL_MARKER (': ')
            ReviewTokenType.TEXT ('term')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testコメント各種をレクスできる() {
        val text = """
            #@ comment
            #@mapfile(foo.rb)
            #@end
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            ReviewTokenType.LINE_COMMENT ('#@ comment')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.LINE_COMMENT ('#@mapfile(foo.rb)')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.LINE_COMMENT ('#@end')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test未終端インラインはEOLで回復する() {
        val text = "@<b>{unterminated\nnext line\n"
        doTest(
            text,
            """
            ReviewTokenType.INLINE_START ('@<')
            ReviewTokenType.INLINE_NAME ('b')
            ReviewTokenType.INLINE_TAG_CLOSE ('>')
            ReviewTokenType.INLINE_LBRACE ('{')
            ReviewTokenType.INLINE_CONTENT ('unterminated')
            ReviewTokenType.EOL ('\n')
            ReviewTokenType.TEXT ('next line')
            ReviewTokenType.EOL ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }
}
