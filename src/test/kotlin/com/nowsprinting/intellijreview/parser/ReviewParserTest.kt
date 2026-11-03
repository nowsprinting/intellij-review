package com.nowsprinting.intellijreview.parser

import com.intellij.testFramework.ParsingTestCase

class ReviewParserTest : ParsingTestCase("parser", "re", ReviewParserDefinition()) {
    override fun getTestDataPath(): String = "src/test/testData"

    fun test見出しとコメントと段落を含むファイルをパースできる() {
        doCodeTest(
            """
            =Chapter 1
            #@# comment
            Hello, Re:VIEW.
            """.trimIndent() + "\n"
        )
    }

    fun test章構造をパースできる() {
        doCodeTest("==[column]{sec1}Section Title\n")
    }

    fun testブロックとボディをパースできる() {
        doCodeTest(
            """
            //list[id1][caption]{
            body line 1
            body line 2
            //}
            """.trimIndent() + "\n"
        )
    }

    fun testインライン入り段落をパースできる() {
        doCodeTest("Hello @<b>{world}!\n")
    }

    fun testエラー回復_壊れた行が次行に波及しない() {
        doCodeTest("Hello @<b>{world\nNext paragraph.\n")
    }

    fun testエラー回復_見出しオプションが未終端でも次行に波及しない() {
        doCodeTest("==[bad\nNext line.\n")
    }
}
