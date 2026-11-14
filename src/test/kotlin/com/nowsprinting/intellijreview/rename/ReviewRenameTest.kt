package com.nowsprinting.intellijreview.rename

import com.intellij.lang.LanguageNamesValidation
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.nowsprinting.intellijreview.lang.ReviewLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewRenameTest : BasePlatformTestCase() {
    // PsiElement.getTextOffset()の既定実装は要素全体の開始位置を返すため、宣言位置に
    // キャレットを置いたrenameElementAtCaret()(実際のShift+F6操作と同じ経路)が
    // TargetElementUtilのgetNamedElement()内「親のgetTextOffset() == キャレット位置の
    // 葉の開始位置」判定に失敗し、実測でnullを返すことを確認した。ReviewBlockCommandMixin/
    // ReviewHeadingMixinにgetTextOffset()をID/ラベル葉の開始位置で上書きすることで解消し、
    // 宣言位置からのShift+F6リネームを実際の操作経路(キャレット→TargetElementUtil)で検証する。
    fun testブロックIDをリネームすると参照が追随する() {
        myFixture.configureByText(
            "rename_block_id.re",
            "//list[fi<caret>g1][caption]{\nbody\n//}\n@<list>{fig1}\n"
        )

        myFixture.renameElementAtCaret("fig2")

        myFixture.checkResult("//list[fig2][caption]{\nbody\n//}\n@<list>{fig2}\n")
    }

    fun test見出しラベルをリネームすると参照が追随する() {
        myFixture.configureByText(
            "rename_heading_label.re",
            "=Title\n=={lb<caret>l1}Sub Title\n@<hd>{lbl1}\n"
        )

        myFixture.renameElementAtCaret("lbl2")

        myFixture.checkResult("=Title\n=={lbl2}Sub Title\n@<hd>{lbl2}\n")
    }

    fun test見出しラベルをインプレースリネームすると定義と参照が追随する() {
        myFixture.configureByText(
            "rename_heading_label_inplace.re",
            "=Title\n=={lb<caret>l1}Sub Title\n@<hd>{lbl1}\n"
        )

        CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "lbl-2.x", myFixture)

        myFixture.checkResult("=Title\n=={lbl-2.x}Sub Title\n@<hd>{lbl-2.x}\n")
    }

    fun testハイフンやドットを含むIDは有効な名前として扱われる() {
        assertTrue(LanguageNamesValidation.isIdentifier(ReviewLanguage, "foo-bar.baz", project))
    }

    fun test空白や括弧を含むIDは無効な名前として扱われる() {
        listOf("", "foo bar", "foo]", "foo}", "foo|bar").forEach {
            assertFalse(it, LanguageNamesValidation.isIdentifier(ReviewLanguage, it, project))
        }
    }

    fun testラベル無し見出しをリネームするとキャプションと参照が追随する() {
        myFixture.configureByText(
            "rename_caption.re",
            "=Title\n== Sub Ti<caret>tle\n@<hd>{Sub Title}\n"
        )

        myFixture.renameElementAtCaret("New Title")

        myFixture.checkResult("=Title\n== New Title\n@<hd>{New Title}\n")
    }

    fun testネストパスや章IDを含む参照はリネーム対象のセグメントだけが変わる() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - rename_path1.re\n  - rename_path2.re\n")
        myFixture.configureByText(
            "rename_path1.re",
            "=Title\n==Sec\n===Su<caret>b\n@<hd>{Sec|Sub}\n"
        )
        val usageFile = myFixture.addFileToProject("rename_path2.re", "=Other\n@<hd>{rename_path1|Sec|Sub}\n")

        myFixture.renameElementAtCaret("New")

        myFixture.checkResult("=Title\n==Sec\n===New\n@<hd>{Sec|New}\n")
        assertEquals("=Other\n@<hd>{rename_path1|Sec|New}\n", usageFile.text)
    }

    fun test親見出しをリネームすると子孫見出しへの参照のパスも追随する() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - rename_parent1.re\n  - rename_parent2.re\n")
        myFixture.configureByText(
            "rename_parent1.re",
            "=Title\n==Se<caret>c\n===Sub\n====Leaf\n@<hd>{Sec}\n@<hd>{Sec|Sub}\n@<hd>{Sec|Sub|Leaf}\n@<hd>{Sub}\n"
        )
        val usageFile = myFixture.addFileToProject("rename_parent2.re", "=Other\n@<hd>{rename_parent1|Sec|Sub}\n")

        myFixture.renameElementAtCaret("New")

        myFixture.checkResult(
            "=Title\n==New\n===Sub\n====Leaf\n@<hd>{New}\n@<hd>{New|Sub}\n@<hd>{New|Sub|Leaf}\n@<hd>{Sub}\n"
        )
        assertEquals("=Other\n@<hd>{rename_parent1|New|Sub}\n", usageFile.text)
    }

    fun testインライン命令を含むキャプションの見出しはリネーム開始前に拒否される() {
        myFixture.configureByText(
            "rename_veto.re",
            "=Title\n==Sub @<code>{x}\n==Plain Title\n=={lbl1}Labeled\n"
        )
        val headings = PsiTreeUtil.findChildrenOfType(myFixture.file, ReviewHeadingMixin::class.java)

        assertTrue(PsiElementRenameHandler.isVetoed(headings.single { it.text.startsWith("==Sub") }))
        assertFalse(PsiElementRenameHandler.isVetoed(headings.single { it.text.startsWith("==Plain") }))
        assertFalse(PsiElementRenameHandler.isVetoed(headings.single { it.getLabel() == "lbl1" }))
    }

    fun testラベル無し見出しは空白入りのキャプションを有効な名前として扱う() {
        myFixture.configureByText("rename_caption_valid.re", "=Title\n==Sub Title\n=={lbl1}Labeled\n")
        val headings = PsiTreeUtil.findChildrenOfType(myFixture.file, ReviewHeadingMixin::class.java)
        val unlabeled = headings.single { it.text.startsWith("==Sub") }
        val labeled = headings.single { it.getLabel() == "lbl1" }

        assertTrue(RenameUtil.isValidName(project, unlabeled, "New Title"))
        listOf(" New", "New ", "a|b", "a{b", "a]b", "a@<b>{c}").forEach {
            assertFalse(it, RenameUtil.isValidName(project, unlabeled, it))
        }
        assertFalse(RenameUtil.isValidName(project, labeled, "New Title"))
    }

    fun testファイルをリネームするとchap参照が追随する() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - rename_chapter1.re\n")
        val chapterFile = myFixture.addFileToProject("rename_chapter1.re", "=Chapter 1\n")
        val usageFile = myFixture.addFileToProject("rename_chapter2.re", "@<chap>{rename_chapter1}\n")

        myFixture.renameElement(chapterFile, "rename_chapter1_renamed.re")

        assertEquals("@<chap>{rename_chapter1_renamed}\n", usageFile.text)
    }
}
