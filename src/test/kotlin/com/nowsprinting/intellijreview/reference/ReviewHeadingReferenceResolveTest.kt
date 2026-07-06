package com.nowsprinting.intellijreview.reference

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewHeadingReferenceResolveTest : BasePlatformTestCase() {
    fun test明示ラベルで見出しに解決される() {
        myFixture.configureByText(
            "a.re",
            "=Title\n=={lbl1}Sub Title\n@<hd>{<caret>lbl1}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewHeadingMixin
        assertEquals("lbl1", resolved?.getLabel())
    }

    fun testキャプションで見出しに解決される() {
        // マーカー直後の空白がTEXTトークンに含まれる実際のRe:VIEW記法("== Caption")で、
        // ReviewDeclarationCache.normalizeCaption()のtrim()が効いていることを検証する。
        myFixture.configureByText(
            "a.re",
            "=Title\n== Sub Title\n@<hd>{Sub Tit<caret>le}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        assertNotNull(reference.resolve())
    }

    fun testネストパスSecSubsecで見出しに解決される() {
        myFixture.configureByText(
            "a.re",
            "=Title\n== Sec\n=== Subsec\n@<hd>{Sec|Sub<caret>sec}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        assertNotNull(reference.resolve())
    }

    fun test章をまたいだchapID_Labelで見出しに解決される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - chapter2.re\n")
        myFixture.addFileToProject("chapter1.re", "=Title\n=={lbl1}Sub\n")
        myFixture.configureByText("chapter2.re", "@<hd>{chapter1|lbl<caret>1}\n")

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewHeadingMixin
        assertEquals("lbl1", resolved?.getLabel())
    }

    fun testcolumn参照はcolumn付き見出しに解決される() {
        myFixture.configureByText(
            "a.re",
            "=Title\n==[column]{col1}Column Title\n@<column>{col<caret>1}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewHeadingMixin
        assertEquals(true, resolved?.isColumn())
    }

    fun testラベルを持つ見出しはキャプションでは解決されない() {
        myFixture.configureByText(
            "a.re",
            "=Title\n=={lbl1}Some Caption\n@<hd>{Some Cap<caret>tion}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        assertNull(reference.resolve())
    }
}
