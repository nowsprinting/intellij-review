package com.nowsprinting.intellijreview.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewContinuationEnterHandlerTest : BasePlatformTestCase() {
    private fun doTest(before: String, after: String) {
        myFixture.configureByText("sample.re", before)
        myFixture.type('\n')
        myFixture.checkResult(after)
    }

    fun test箇条書きの行末でEnterすると次行に同じマーカーが入る() =
        doTest(" * foo<caret>\n", " * foo\n * <caret>\n")

    fun testネストした箇条書きはネストレベルを維持する() =
        doTest(" ** foo<caret>\n", " ** foo\n ** <caret>\n")

    fun test番号付きリストは番号がインクリメントされる() =
        doTest(" 9. foo<caret>\n", " 9. foo\n 10. <caret>\n")

    fun test行の途中でEnterすると後半が新しい項目になる() =
        doTest(" * foo<caret>bar\n", " * foo\n * <caret>bar\n")

    fun test空の項目でEnterするとマーカーが消える() =
        doTest(" * foo\n * <caret>\n", " * foo\n<caret>\n")

    fun test行頭に空白のないアスタリスクは継続しない() =
        doTest("* foo<caret>\n", "* foo\n<caret>\n")

    fun testコメント行の行末でEnterすると次行にコメント記号が入る() =
        doTest("#@# foo<caret>\n", "#@# foo\n#@# <caret>\n")

    fun testコメント行の途中でEnterすると後半もコメントになる() =
        doTest("#@# foo<caret>bar\n", "#@# foo\n#@# <caret>bar\n")

    fun test空のコメント行でEnterするとコメント記号が消える() =
        doTest("#@# foo\n#@# <caret>\n", "#@# foo\n<caret>\n")

    fun testブロック本文中のリスト風の行は継続しない() =
        doTest("//emlist{\n * foo<caret>\n//}\n", "//emlist{\n * foo\n <caret>\n//}\n")
}
