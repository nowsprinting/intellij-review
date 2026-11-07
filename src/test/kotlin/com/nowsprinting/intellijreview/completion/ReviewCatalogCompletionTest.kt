package com.nowsprinting.intellijreview.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewCatalogCompletionTest : BasePlatformTestCase() {
    fun testcatalogに未登録のreファイル名だけが補完される() {
        myFixture.addFileToProject("chapter1.re", "")
        myFixture.addFileToProject("chapter2.re", "")
        myFixture.addFileToProject("chapter3.re", "")
        myFixture.addFileToProject("notes.txt", "")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - chapter1.re\n  - <caret>\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "chapter2.re", "chapter3.re")
    }

    fun testドットまで入力してもファイル名全体で置き換わる() {
        myFixture.addFileToProject("chapter1.re", "")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - chapter1.r<caret>\n")
        myFixture.completeBasic()
        myFixture.checkResult("CHAPS:\n  - chapter1.re<caret>\n")
    }

    fun testcontentdir配下のreファイル名が補完される() {
        myFixture.addFileToProject("config.yml", "contentdir: src\n")
        myFixture.addFileToProject("src/a.re", "")
        myFixture.addFileToProject("src/b.re", "")
        myFixture.addFileToProject("c.re", "")
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - <caret>\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "a.re", "b.re")
    }

    fun testシーケンス項目以外ではreファイル名が補完されない() {
        myFixture.addFileToProject("a.re", "")
        myFixture.addFileToProject("b.re", "")
        myFixture.configureByText("catalog.yml", "CHA<caret>\n")
        myFixture.completeBasic()
        assertEmpty(myFixture.lookupElementStrings.orEmpty())
    }

    fun test空のcatalogでは全セクションキーが補完される() {
        myFixture.configureByText("catalog.yml", "<caret>")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "PREDEF:", "CHAPS:", "APPENDIX:", "POSTDEF:")
    }

    fun test記述済みのセクションキーは補完されない() {
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - a.re\n<caret>\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "PREDEF:", "APPENDIX:", "POSTDEF:")
    }

    fun testセクションキーはコロン付きで挿入される() {
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - a.re\nAPP<caret>\n")
        myFixture.completeBasic()
        myFixture.checkResult("CHAPS:\n  - a.re\nAPPENDIX:<caret>\n")
    }

    fun testインデントされた行ではセクションキーが補完されない() {
        myFixture.configureByText("catalog.yml", "CHAPS:\n  - a.re\n  CH<caret>\n")
        myFixture.completeBasic()
        assertEmpty(myFixture.lookupElementStrings.orEmpty())
    }
}
