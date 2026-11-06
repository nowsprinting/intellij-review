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
            heading marker '=' ('=')
            text ('Chapter 1')
            end of line ('\n')
            text ('Hello')
            end of line ('\n')

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
            heading marker '=' ('=')
            text (' Level 1')
            end of line ('\n')
            heading marker '=' ('==')
            text (' Level 2')
            end of line ('\n')
            heading marker '=' ('===')
            text (' Level 3')
            end of line ('\n')
            heading marker '=' ('====')
            text (' Level 4')
            end of line ('\n')
            heading marker '=' ('=====')
            text (' Level 5')
            end of line ('\n')
            heading marker '=' ('======')
            text (' Level 6')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test見出しのオプションとラベルをレクスできる() {
        val text = "=[column]{chap1} Introduction\n"
        doTest(
            text,
            """
            heading marker '=' ('=')
            [ ('[')
            heading option ('column')
            ] (']')
            heading label '{' ('{')
            heading label ('chap1')
            heading label '}' ('}')
            text (' Introduction')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testブロック命令の1行形式をレクスできる() {
        val text = "//noheader\n//list[id1]\n"
        doTest(
            text,
            """
            block command name ('//noheader')
            end of line ('\n')
            block command name ('//list')
            [ ('[')
            block argument ('id1')
            ] (']')
            end of line ('\n')

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
            block command name ('//list')
            [ ('[')
            block argument ('id1')
            ] (']')
            [ ('[')
            block argument ('caption1')
            ] (']')
            block body '{' ('{')
            end of line ('\n')
            block body text ('line1')
            end of line ('\n')
            block body text ('line2')
            end of line ('\n')
            //} ('//}')
            end of line ('\n')

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
            block command name ('//list')
            [ ('[')
            block argument ('id1')
            ] (']')
            block body '{' ('{')
            end of line ('\n')
            block body text ('plain text ')
            @< ('@<')
            inline command name ('b')
            > ('>')
            { ('{')
            inline content ('bold')
            } ('}')
            block body text (' more text')
            end of line ('\n')
            //} ('//}')
            end of line ('\n')

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
            text ('intro paragraph')
            end of line ('\n')
            block command name ('//list')
            [ ('[')
            block argument ('id')
            ] (']')
            block body '{' ('{')
            end of line ('\n')
            block body text ('body line')
            end of line ('\n')
            //} ('//}')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testインラインのエスケープとフェンスをレクスできる() {
        val text = "@<b>{esc\\}aped} @<m>\$a+b\$ @<m>|pipe|\n"
        doTest(
            text,
            """
            @< ('@<')
            inline command name ('b')
            > ('>')
            { ('{')
            inline content ('esc')
            inline content ('\}')
            inline content ('aped')
            } ('}')
            text (' ')
            @< ('@<')
            inline command name ('m')
            > ('>')
            inline fence '$'/'|' ('${'$'}')
            inline content ('a+b')
            inline fence '$'/'|' ('${'$'}')
            text (' ')
            @< ('@<')
            inline command name ('m')
            > ('>')
            inline fence '$'/'|' ('|')
            inline content ('pipe')
            inline fence '$'/'|' ('|')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test箇条書き3種をレクスできる() {
        val text = """
            | * item1
            | ** item2
            | 1. first
            | : term
        """.trimMargin() + "\n"
        doTest(
            text,
            """
            list marker '*' (' * ')
            text ('item1')
            end of line ('\n')
            list marker '*' (' ** ')
            text ('item2')
            end of line ('\n')
            list marker '1.' (' 1. ')
            text ('first')
            end of line ('\n')
            list marker ':' (' : ')
            text ('term')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun testシャープアットシャープで始まる行だけを行コメントとしてレクスする() {
        val text = """
            #@# comment
            #@#comment
            #@mapfile(foo.rb)
        """.trimIndent() + "\n"
        doTest(
            text,
            """
            comment '#@#' ('#@# comment')
            end of line ('\n')
            comment '#@#' ('#@#comment')
            end of line ('\n')
            text ('#')
            text ('@')
            text ('mapfile(foo.rb)')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }

    fun test未終端インラインはEOLで回復する() {
        val text = "@<b>{unterminated\nnext line\n"
        doTest(
            text,
            """
            @< ('@<')
            inline command name ('b')
            > ('>')
            { ('{')
            inline content ('unterminated')
            end of line ('\n')
            text ('next line')
            end of line ('\n')

            """.trimIndent()
        )
        checkCorrectRestart(text)
    }
}
