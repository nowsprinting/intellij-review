package com.nowsprinting.intellijreview.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import com.nowsprinting.intellijreview.parser.ReviewParserDefinition
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

class ReviewPsiMixinTest : ParsingTestCase("psi", "re", ReviewParserDefinition()) {
    override fun getTestDataPath(): String = "src/test/testData"

    fun test見出しのレベルはマーカーの文字数() {
        val file = createPsiFile("a", "===Chapter\n")
        val heading = PsiTreeUtil.findChildOfType(file, ReviewHeadingMixin::class.java)!!
        assertEquals(3, heading.getLevel())
        assertFalse(heading.isColumn())
    }

    fun testcolumnオプション付き見出しはisColumnがtrueを返す() {
        val file = createPsiFile("a", "=[column]Chapter\n")
        val heading = PsiTreeUtil.findChildOfType(file, ReviewHeadingMixin::class.java)!!
        assertTrue(heading.isColumn())
    }

    fun testブロック命令名は先頭の2文字を除いた部分() {
        val file = createPsiFile(
            "a",
            """
            //list[id1][caption]{
            body
            //}
            """.trimIndent() + "\n"
        )
        val block = PsiTreeUtil.findChildOfType(file, ReviewBlockCommandMixin::class.java)!!
        assertEquals("list", block.getCommandName())
        assertEquals("id1", block.getIdArg())
    }

    fun testID引数を取らない命令はgetIdArgがnullを返す() {
        val file = createPsiFile("a", "//noheader\n")
        val block = PsiTreeUtil.findChildOfType(file, ReviewBlockCommandMixin::class.java)!!
        assertEquals("noheader", block.getCommandName())
        assertNull(block.getIdArg())
    }

    fun testインライン命令名と本文を取得できる() {
        val file = createPsiFile("a", "@<b>{world}\n")
        val inline = PsiTreeUtil.findChildOfType(file, ReviewInlineCommandMixin::class.java)!!
        assertEquals("b", inline.getCommandName())
        assertEquals("world", inline.getContentText())
    }

    fun testブレース形式のみバックスラッシュエスケープが解除される() {
        val brace = PsiTreeUtil.findChildOfType(
            createPsiFile("a", "@<b>{a\\}b}\n"),
            ReviewInlineCommandMixin::class.java
        )!!
        assertEquals("a}b", brace.getContentText())

        val fence = PsiTreeUtil.findChildOfType(
            createPsiFile("a", "@<b>\$a\\}b\$\n"),
            ReviewInlineCommandMixin::class.java
        )!!
        assertEquals("a\\}b", fence.getContentText())
    }
}
