package com.nowsprinting.intellijreview.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewCompletionTest : BasePlatformTestCase() {
    fun testブロック名を補完できる() {
        myFixture.configureByText("a.re", "//<caret>\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("list"))
        assertTrue(strings.contains("image"))
        assertTrue(strings.contains("footnote"))
    }

    fun testブロック命令選択で引数テンプレートが挿入される() {
        myFixture.configureByText("a.re", "//<caret>\n")
        myFixture.completeBasic()
        myFixture.type("list\n")
        myFixture.checkResult("//list[<caret>][]{\n\n//}\n")
    }

    fun testインライン名を補完できる() {
        myFixture.configureByText("a.re", "@<<caret>\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("b"))
        assertTrue(strings.contains("hd"))
        assertTrue(strings.contains("list"))
        assertTrue(strings.contains("fn"))
    }

    fun testインライン命令選択で山括弧と波括弧が挿入されキャレットが中に入る() {
        myFixture.configureByText("a.re", "@<<caret>\n")
        myFixture.completeBasic()
        myFixture.type("b\n")
        myFixture.checkResult("@<b>{<caret>}\n")
    }

    fun test見出しオプションを補完できる() {
        myFixture.configureByText("a.re", "=[<caret>]Title\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("column"))
        assertTrue(strings.contains("nonum"))
        assertTrue(strings.contains("notoc"))
        assertTrue(strings.contains("nodisp"))
    }

    fun test章IDはcatalog順に補完される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - chapter2.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.addFileToProject("chapter2.re", "= Chapter 2\n")
        myFixture.configureByText("chapter3.re", "@<chap>{<caret>}\n")

        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("chapter1"))
        assertTrue(strings.contains("chapter2"))
        assertTrue(strings.indexOf("chapter1") < strings.indexOf("chapter2"))
    }

    fun testlistのIDを同一ファイルから補完できる() {
        myFixture.configureByText(
            "a.re",
            """
            //list[fig1][caption]{
            body
            //}
            @<list>{<caret>}
            """.trimIndent() + "\n"
        )
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings!!.contains("fig1"))
    }

    fun testhdのラベルを補完できる() {
        // レベル1見出し(章タイトル)はRe:VIEW本体のheadline_indexに含まれず@<hd>では
        // 解決できない(Step 7で判明。deepwikiで実測確認)ため、レベル2見出しを使う。
        myFixture.configureByText(
            "a.re",
            "=Title\n=={lbl1}Sub Title\n@<hd>{<caret>}\n"
        )
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings!!.contains("lbl1"))
    }

    fun testfnは同一ファイルの脚注のみ補完される() {
        myFixture.addFileToProject("other.re", "//footnote[other1][other note]\n")
        myFixture.configureByText(
            "a.re",
            "//footnote[note1][a note]\n@<fn>{<caret>}\n"
        )
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("note1"))
        assertFalse(strings.contains("other1"))
    }

    fun testfootnote選択で括弧2つのテンプレートが挿入される() {
        myFixture.configureByText("a.re", "//<caret>\n")
        myFixture.completeBasic()
        myFixture.type("footnote\n")
        myFixture.checkResult("//footnote[<caret>][]\n")
    }

    fun testendnoteは同一ファイルの後注のみ補完される() {
        myFixture.addFileToProject("other.re", "//endnote[other1][other note]\n")
        myFixture.configureByText(
            "a.re",
            "//endnote[note1][a note]\n@<endnote>{<caret>}\n"
        )
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("note1"))
        assertFalse(strings.contains("other1"))
    }

    fun testbibは章に関わらずbib_reの全項目から補完される() {
        myFixture.addFileToProject("bib.re", "//bibpaper[ref1][Some Paper]\n")
        myFixture.configureByText("a.re", "@<bib>{<caret>}\n")
        myFixture.completeBasic()
        assertTrue(myFixture.lookupElementStrings!!.contains("ref1"))
    }

    fun testrefは同一ファイルのlabelのみ補完される() {
        myFixture.addFileToProject("other.re", "//label[other1]\n")
        myFixture.configureByText("a.re", "//label[label1]\n@<ref>{<caret>}\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("label1"))
        assertFalse(strings.contains("other1"))
    }
}
