package com.nowsprinting.intellijreview.usages

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

class ReviewFindUsagesTest : BasePlatformTestCase() {
    // DefaultWordsScannerが識別子境界でトークンを分割してしまうと、ハイフン入りIDや
    // 日本語キャプションのワードインデックスが壊れ、Find Usagesが黙って見逃す恐れがある
    // ため回帰検知として追加した(advisorレビューで指摘)。
    //
    // ファイル名は"a.re"/"b.re"のような使い回されがちな名前を避け、テストごとに一意な
    // 名前を使う。BasePlatformTestCaseの軽量プロジェクトは同一JVM内の他テストクラスと
    // VFS/FileBasedIndexを共有するため、他クラスが同名ファイルを異なる内容で使っていると
    // ワードインデックスが古い内容のまま(スケール漏れ)になりFind Usagesが黙って0件を返す
    // ことを実測で確認した(実装のバグではなくテスト間のファイル名衝突が原因)。
    fun testハイフン入りIDと日本語キャプションが被リンクとして見つかる() {
        // 見出しの被リンクはPsiNamedElement.getName()(=明示ラベル)を検索語として使うため、
        // キャプションのみの見出しでは検索語自体が存在せず被リンクを見つけようがない
        // (これはバグではなく設計どおり。@<hd>のキャプション一致自体はStep 7で別途検証済み)。
        // そのため日本語の検証は明示ラベルで行う。
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
