package com.nowsprinting.intellijreview.reference

import com.intellij.codeInsight.hint.ImplementationViewComponent
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewImplementationTextSelectionerTest : BasePlatformTestCase() {
    private fun definitionTextOf(text: String, label: String): String? {
        myFixture.configureByText("a.re", text)
        val heading = PsiTreeUtil.findChildrenOfType(myFixture.file, ReviewHeadingMixin::class.java)
            .first { it.getLabel() == label }
        return ImplementationViewComponent.getNewText(heading)
    }

    fun test見出しの定義は同位の次の見出しの手前までのセクション本文を含む() {
        val text = "= 章\n\n=={a} 節A\n\n本文A\n\n==={a1} 項\n\n本文A1\n\n=={b} 節B\n\n本文B\n"

        assertEquals("=={a} 節A\n\n本文A\n\n==={a1} 項\n\n本文A1", definitionTextOf(text, "a"))
    }

    fun test最後の見出しの定義はファイル末尾までを含む() {
        val text = "= 章\n\n=={a} 節A\n\n本文A\n"

        assertEquals("=={a} 節A\n\n本文A", definitionTextOf(text, "a"))
    }

    fun testコラム見出しの定義はコラム終了マーカーの手前までを含む() {
        val text = "= 章\n\n==[column]{c} コラム\n\n本文C\n\n==[/column]\n\n後続\n"

        assertEquals("==[column]{c} コラム\n\n本文C", definitionTextOf(text, "c"))
    }
}
