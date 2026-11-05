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

    fun testreview_ext_rbでdefinlineされたユーザー命令もインライン名の候補に出る() {
        myFixture.addFileToProject(
            "review-ext.rb",
            """
            module ReVIEW
              module BuilderOverride
                Compiler.definline :bidx
                Compiler.definline(:iidx)
                # Compiler.definline :commented
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("a.re", "@<<caret>\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("b"))
        assertTrue(strings.contains("bidx"))
        assertTrue(strings.contains("iidx"))
        assertFalse(strings.contains("commented"))
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

    fun testimgの候補に同一ファイルの画像と章IDが出て他章の画像は出ない() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n//image[other1][caption]\n")
        myFixture.configureByText("a.re", "= A\n//image[img1][caption]\n@<img>{<caret>}\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.contains("img1"))
        assertTrue(strings.contains("chapter1"))
        assertFalse(strings.contains("other1"))
    }

    fun testimgで章IDを選ぶとパイプが挿入されキャレットがその後ろに入る() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n//image[other1][caption]\n")
        myFixture.configureByText("a.re", "= A\n@<img>{chapt<caret>}\n")
        myFixture.completeBasic()
        myFixture.checkResult("= A\n@<img>{chapter1|<caret>}\n")
    }

    fun testimgで章IDとパイプの後は当該章の画像のみ補完される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n//image[other1][caption]\n")
        myFixture.configureByText("a.re", "= A\n//image[img1][caption]\n@<img>{chapter1|<caret>}\n")
        myFixture.completeBasic()
        assertEquals(listOf("other1"), myFixture.lookupElementStrings)
    }

    fun testimageのIDに章名ディレクトリ下の画像ファイル名が拡張子なしで補完される() {
        myFixture.addFileToProject("images/a/fig1.png", "")
        myFixture.addFileToProject("images/a/fig2.pdf", "")
        myFixture.addFileToProject("images/a/notes.txt", "")
        myFixture.addFileToProject("images/other/fig3.png", "")
        myFixture.configureByText("a.re", "//image[<caret>][caption]\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "fig1", "fig2")
    }

    fun testindepimageのIDに章名ディレクトリ下の画像ファイル名が補完される() {
        myFixture.addFileToProject("images/a/fig1.png", "")
        myFixture.addFileToProject("images/a/fig2.png", "")
        myFixture.configureByText("a.re", "//indepimage[<caret>]\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "fig1", "fig2")
    }

    fun testnumberlessimageのIDに章名ディレクトリ下の画像ファイル名が補完される() {
        myFixture.addFileToProject("images/a/fig1.png", "")
        myFixture.addFileToProject("images/a/fig2.png", "")
        myFixture.configureByText("a.re", "//numberlessimage[<caret>][caption]\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "fig1", "fig2")
    }

    fun testimageの2つ目の引数では画像ファイル名は補完されない() {
        myFixture.addFileToProject("images/a/fig1.png", "")
        myFixture.configureByText("a.re", "//image[id][<caret>]\n")
        myFixture.completeBasic()
        assertFalse(myFixture.lookupElementStrings.orEmpty().contains("fig1"))
    }

    fun testchapで章IDを選んでもパイプは挿入されない() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n")
        myFixture.configureByText("a.re", "= A\n@<chap>{chapt<caret>}\n")
        myFixture.completeBasic()
        myFixture.checkResult("= A\n@<chap>{chapter1<caret>}\n")
    }

    fun testhdで章IDとパイプの後は節も項もフラットに補完される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n=={sec}Sec Title\n=== Sub\n")
        myFixture.configureByText("a.re", "= A\n@<hd>{chapter1|<caret>}\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "sec", "Sub")
    }

    fun testhdの項の候補には所属する節タイトルが表示される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n=={sec}Sec Title\n=== Sub\n")
        myFixture.configureByText("a.re", "= A\n@<hd>{chapter1|<caret>}\n")
        myFixture.completeBasic()
        val sub = myFixture.lookupElements!!.single { it.lookupString == "Sub" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation.renderElement(sub)
        assertEquals("Sec Title", presentation.typeText)
    }

    fun testhdで同名の項が複数ある場合はネストパスで補完される() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n== S1\n=== Sub\n== S2\n=== Sub\n")
        myFixture.configureByText("a.re", "= A\n@<hd>{chapter1|<caret>}\n")
        myFixture.completeBasic()
        assertSameElements(myFixture.lookupElementStrings!!, "S1", "S2", "S1|Sub", "S2|Sub")
    }

    fun testhdで同一ファイル内の節も項もフラットに補完され項には節タイトルが表示される() {
        myFixture.configureByText("a.re", "= A\n=={sec}Sec Title\n=== Sub\n@<hd>{<caret>}\n")
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings!!
        assertTrue(strings.containsAll(listOf("sec", "Sub")))
        assertFalse(strings.contains("sec|Sub"))
        val sub = myFixture.lookupElements!!.single { it.lookupString == "Sub" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation.renderElement(sub)
        assertEquals("Sec Title", presentation.typeText)
    }
}
