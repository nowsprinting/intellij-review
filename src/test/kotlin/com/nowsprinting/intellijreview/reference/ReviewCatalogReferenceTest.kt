package com.nowsprinting.intellijreview.reference

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewCatalogReferenceTest : BasePlatformTestCase() {
    fun testcatalog中のreファイル名は章ファイルに解決される() {
        val chapterFile = myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - chap<caret>ter1.re\n")

        assertEquals(chapterFile, myFixture.getReferenceAtCaretPosition()!!.resolve())
    }

    fun testcatalog以外のymlではreファイル名に参照が付かない() {
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("config.yml", "CHAPS:\n  - chap<caret>ter1.re\n")

        assertNull(myFixture.getReferenceAtCaretPosition())
    }

    fun test章ファイルをリネームするとcatalogの記述も更新される() {
        val chapterFile = myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - chapter1.re\n")

        myFixture.renameElement(chapterFile, "intro.re")

        myFixture.checkResult("CHAPS:\n  - intro.re\n")
    }

    fun testconfigのcontentdir配下の章ファイルに解決される() {
        myFixture.addFileToProject("config.yml", "contentdir: \"src\" # 原稿\n")
        val chapterFile = myFixture.addFileToProject("src/chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - chap<caret>ter1.re\n")

        assertEquals(chapterFile, myFixture.getReferenceAtCaretPosition()!!.resolve())
    }
}
