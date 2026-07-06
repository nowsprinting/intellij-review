package com.nowsprinting.intellijreview.reference

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

class ReviewReferenceResolveTest : BasePlatformTestCase() {
    fun test章参照はファイルに解決される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n")
        val chapterFile = myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("a.re", "@<chap>{chap<caret>ter1}\n")

        val reference = myFixture.getReferenceAtCaretPosition()!!
        assertEquals(chapterFile, reference.resolve())
    }

    fun testlist参照は同一ファイルのブロックに解決される() {
        myFixture.configureByText(
            "a.re",
            "//list[fig1][caption]{\nbody\n//}\n@<list>{fig<caret>1}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewBlockCommandMixin
        assertEquals("fig1", resolved?.getIdArg())
    }

    fun testimg参照はimage系命令をkindでグルーピングして解決される() {
        myFixture.configureByText(
            "a.re",
            "//graph[pic1][caption]{\nbody\n//}\n@<img>{pic<caret>1}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewBlockCommandMixin
        assertEquals("graph", resolved?.getCommandName())
    }

    fun testchapIdを跨いだ参照は章参照とブロックID参照の2つに分割される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - chapter3.re\n")
        val chapter1File = myFixture.addFileToProject("chapter1.re", "//list[fig1][caption]{\nbody\n//}\n")
        myFixture.configureByText("chapter3.re", "@<list>{chapter1|fig1}\n")

        val inlineCommand = PsiTreeUtil.findChildOfType(myFixture.file, ReviewInlineCommandMixin::class.java)!!
        val references = inlineCommand.references
        assertEquals(2, references.size)
        assertEquals(chapter1File, references[0].resolve())
        val blockResolved = references[1].resolve() as? ReviewBlockCommandMixin
        assertEquals("fig1", blockResolved?.getIdArg())
    }

    fun testfn参照は同一ファイル限定で他ファイルのidは解決されない() {
        myFixture.addFileToProject("other.re", "//footnote[other1][note]\n")
        myFixture.configureByText(
            "a.re",
            "//footnote[note1][a note]\n@<fn>{note<caret>1}\n"
        )
        assertNotNull(myFixture.getReferenceAtCaretPosition()!!.resolve())

        myFixture.configureByText("b.re", "@<fn>{other<caret>1}\n")
        assertNull(myFixture.getReferenceAtCaretPosition()!!.resolve())
    }

    fun testbib参照は別ファイルのbib_reに解決される() {
        myFixture.addFileToProject("bib.re", "//bibpaper[ref1][Some Paper]\n")
        myFixture.configureByText("a.re", "@<bib>{ref<caret>1}\n")

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewBlockCommandMixin
        assertEquals("ref1", resolved?.getIdArg())
    }

    fun testref参照はlabel宣言に解決される() {
        myFixture.configureByText(
            "a.re",
            "//label[label1]\n@<ref>{lab<caret>el1}\n"
        )

        val reference = myFixture.getReferenceAtCaretPosition()!!
        val resolved = reference.resolve() as? ReviewBlockCommandMixin
        assertEquals("label1", resolved?.getIdArg())
    }

    fun test宣言が見つからない参照はnullを返す() {
        myFixture.configureByText("a.re", "@<list>{no<caret>pe}\n")

        val reference = myFixture.getReferenceAtCaretPosition()!!
        assertNull(reference.resolve())
    }
}
