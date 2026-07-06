package com.nowsprinting.intellijreview.rename

import com.intellij.psi.util.PsiTreeUtil
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

    fun testラベル無し見出しはリネームできない() {
        myFixture.configureByText(
            "rename_no_label_heading.re",
            "=Title\n==Sub Title\n"
        )
        val heading = PsiTreeUtil.findChildrenOfType(myFixture.file, ReviewHeadingMixin::class.java)
            .single { it.getLevel() == 2 }

        assertNull(heading.nameIdentifier)
    }

    fun testファイルをリネームするとchap参照が追随する() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - rename_chapter1.re\n")
        val chapterFile = myFixture.addFileToProject("rename_chapter1.re", "=Chapter 1\n")
        val usageFile = myFixture.addFileToProject("rename_chapter2.re", "@<chap>{rename_chapter1}\n")

        myFixture.renameElement(chapterFile, "rename_chapter1_renamed.re")

        assertEquals("@<chap>{rename_chapter1_renamed}\n", usageFile.text)
    }
}
