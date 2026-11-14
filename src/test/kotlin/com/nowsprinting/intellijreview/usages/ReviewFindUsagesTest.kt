package com.nowsprinting.intellijreview.usages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewFindUsagesTest : BasePlatformTestCase() {
    // WordsScannerの分割境界が検索側(StringUtil.getWordsIn)とずれると、ハイフン入りIDや
    // スペース入りキャプションの被リンクをFind Usagesが黙って見逃すため、他ファイルからの
    // 章跨ぎ記法も含めて検証する。
    //
    // ファイル名は"a.re"/"b.re"のような使い回されがちな名前を避け、テストごとに一意な
    // 名前を使う。BasePlatformTestCaseの軽量プロジェクトは同一JVM内の他テストクラスと
    // VFS/FileBasedIndexを共有するため、他クラスが同名ファイルを異なる内容で使っていると
    // ワードインデックスが古い内容のまま(スケール漏れ)になりFind Usagesが黙って0件を返す
    // ことを実測で確認した(実装のバグではなくテスト間のファイル名衝突が原因)。
    fun testハイフン入りIDと日本語キャプションが被リンクとして見つかる() {
        myFixture.configureByText(
            "findusages_hyphen_cjk.re",
            "//list[fig-1][caption]{\nbody\n//}\n=Title\n=={日本語ラベル}Sub Title\n@<list>{fig-1}\n@<hd>{日本語ラベル}\n"
        )
        val listDeclaration = PsiTreeUtil.findChildOfType(myFixture.file, ReviewBlockCommandMixin::class.java)!!
        val headingDeclaration = PsiTreeUtil.findChildrenOfType(myFixture.file, ReviewHeadingMixin::class.java)
            .single { it.getLabel() == "日本語ラベル" }

        assertEquals(1, myFixture.findUsages(listDeclaration).size)
        assertEquals(1, myFixture.findUsages(headingDeclaration).size)
    }

    fun testlist宣言の被リンクは他ファイルからも見つかる() {
        // "|"無しの@<list>{id}は同一ファイル限定(Step 6の設計どおり)なので、他ファイルからの
        // 被リンクを検証するには章跨ぎ記法("chapterId|id")とcatalog.ymlによる章登録が必要。
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - findusages_decl_chapter.re\n")
        val declFile = myFixture.addFileToProject("findusages_decl_chapter.re", "//list[fig1][caption]{\nbody\n//}\n")
        myFixture.addFileToProject("findusages_list_usage.re", "@<list>{findusages_decl_chapter|fig1}\n")
        val declaration = PsiTreeUtil.findChildOfType(declFile, ReviewBlockCommandMixin::class.java)!!

        assertEquals(1, myFixture.findUsages(declaration).size)
    }

    fun test見出しラベルの被リンクが見つかる() {
        myFixture.configureByText(
            "findusages_heading_label.re",
            "=Title\n=={lbl1}Sub Title\n@<hd>{lbl1}\n"
        )
        val heading = PsiTreeUtil.findChildrenOfType(myFixture.file, ReviewHeadingMixin::class.java)
            .single { it.getLabel() == "lbl1" }

        assertEquals(1, myFixture.findUsages(heading).size)
    }

    fun test見出しマーカー上のキャレットから見出しラベルの被リンクが見つかる() {
        myFixture.configureByText(
            "findusages_heading_marker.re",
            "=Title\n=<caret>={lbl2}Sub Title\n@<hd>{lbl2}\n"
        )

        assertEquals(1, myFixture.findUsages(targetElementAtCaret()).size)
    }

    fun test章見出しマーカー上のキャレットから章ファイルの被リンクが見つかる() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - findusages_chapter3.re\n")
        myFixture.addFileToProject("findusages_chapter4.re", "@<chapref>{findusages_chapter3}\n")
        myFixture.configureByText("findusages_chapter3.re", "<caret>=Chapter 3\n")

        val usageFiles = myFixture.findUsages(targetElementAtCaret()).map { it.file?.name }.toSet()
        assertEquals(setOf("findusages_chapter4.re", "catalog.yml"), usageFiles)
    }

    fun testキャプションのみの見出しマーカー上のキャレットからキャプション指定の被リンクが見つかる() {
        myFixture.configureByText(
            "findusages_heading_caption.re",
            "=Title\n=<caret>= Foo Bar\n@<hd>{Foo Bar}\n"
        )

        assertEquals(1, myFixture.findUsages(targetElementAtCaret()).size)
    }

    fun testキャプション上のキャレットからキャプション指定の被リンクが見つかる() {
        myFixture.configureByText(
            "findusages_heading_caption_caret.re",
            "=Title\n== Foo B<caret>az\n@<hd>{Foo Baz}\n"
        )

        assertEquals(1, myFixture.findUsages(targetElementAtCaret()).size)
    }

    fun test見出しラベルの被リンクは他章の章跨ぎ記法からも見つかる() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - findusages_hd_decl.re\n  - findusages_hd_usage.re\n")
        myFixture.addFileToProject("findusages_hd_usage.re", "=Usage\n@<hd>{findusages_hd_decl|find-and-setup}\n")
        myFixture.configureByText("findusages_hd_decl.re", "=Decl\n=<caret>={find-and-setup}Sub Title\n")

        assertEquals(1, myFixture.findUsages(targetElementAtCaret()).size)
    }

    fun testスペース入りキャプションの被リンクは他章の章跨ぎ記法からも見つかる() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - findusages_cap_decl.re\n  - findusages_cap_usage.re\n")
        myFixture.addFileToProject("findusages_cap_usage.re", "=Usage\n@<hd>{findusages_cap_decl|Foo Qux}\n")
        myFixture.configureByText("findusages_cap_decl.re", "=Decl\n=<caret>= Foo Qux\n")

        assertEquals(1, myFixture.findUsages(targetElementAtCaret()).size)
    }

    // Find Usagesアクションと同じ経路(TargetElementUtil)でキャレット位置の宣言を解決する。
    private fun targetElementAtCaret() =
        TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.getInstance().allAccepted)!!

    fun test章ファイルの被リンクが見つかる() {
        // catalog.ymlはReviewDeclarationCacheが固定ファイル名で検索するため、
        // このファイル名だけは一意化できない(PSI直読みでFileBasedIndexを経由しないため、
        // 他テストクラスとの再利用による不具合は実測上生じていない)。
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n")
        myFixture.addFileToProject("findusages_chapter2.re", "@<chap>{chapter1}\n")
        val chapterFile = myFixture.addFileToProject("chapter1.re", "=Chapter 1\n")

        val usageFiles = myFixture.findUsages(chapterFile).map { it.file?.name }.toSet()
        assertEquals(setOf("findusages_chapter2.re", "catalog.yml"), usageFiles)
    }
}
