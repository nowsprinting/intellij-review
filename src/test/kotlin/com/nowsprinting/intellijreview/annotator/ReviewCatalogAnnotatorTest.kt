package com.nowsprinting.intellijreview.annotator

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewCatalogAnnotatorTest : BasePlatformTestCase() {
    fun testcatalogに存在しないreファイルを書くとエラーになる() {
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - <error descr=\"Cannot resolve 'nochap.re'\">nochap.re</error>\n")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testcatalogに存在するreファイルを書いてもエラーにならない() {
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - chapter1.re\n")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testcatalog以外のymlでは存在しないreファイルを書いてもエラーにならない() {
        myFixture.configureByText("config.yml", "CHAPS:\n  - nochap.re\n")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testcontentdir配下に無いreファイルを書くとエラーになる() {
        myFixture.addFileToProject("config.yml", "contentdir: src\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - <error descr=\"Cannot resolve 'chapter1.re'\">chapter1.re</error>\n")
        myFixture.checkHighlighting(true, false, false)
    }
}
