package com.nowsprinting.intellijreview.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewAnnotatorTest : BasePlatformTestCase() {
    private fun check(text: String) {
        myFixture.configureByText("a.re", text)
        myFixture.checkHighlighting(true, false, false)
    }

    fun test未定義のインライン命令はエラーになる() {
        check("@<<error descr=\"Unknown inline command: foo\">foo</error>>{x}\n")
    }

    fun testreview_ext_rbでdefinlineされた命令はエラーにならない() {
        myFixture.addFileToProject("review-ext.rb", "Compiler.definline :bidx\n")
        check("@<bidx>{x}\n")
    }

    fun testcontentdir配下の原稿ではベースディレクトリのreview_ext_rbの命令がエラーにならない() {
        myFixture.addFileToProject("config.yml", "contentdir: src\n")
        myFixture.addFileToProject("review-ext.rb", "Compiler.definline :bidx\n")
        val file = myFixture.addFileToProject("src/a.re", "@<bidx>{x}\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.checkHighlighting(true, false, false)
    }

    fun test未定義のブロック命令はエラーになる() {
        check("<error descr=\"Unknown block command: //foo\">//foo</error>[x]\n")
    }

    fun testreview_ext_rbでdefblock_defsingle_defminicolumnされた命令はエラーにならない() {
        myFixture.addFileToProject(
            "review-ext.rb",
            "ReVIEW::Compiler.defblock :myblock, 0..1\nCompiler.defsingle(:mysingle, 1)\nCompiler.defminicolumn :mycolumn, 0..1\n",
        )
        check("//myblock{\nfoo\n//}\n//mysingle[x]\n//mycolumn{\nfoo\n//}\n")
    }

    fun test存在しないIDへの参照はエラーになる() {
        check("@<list>{<error descr=\"Cannot resolve 'nope'\">nope</error>}\n")
    }

    fun test存在するIDへの参照はエラーにならない() {
        check("//list[l1][cap]{\nfoo\n//}\n@<list>{l1}\n")
    }

    fun test存在しない章への参照はエラーになる() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - a.re\n")
        check("@<chap>{<error descr=\"Cannot resolve 'nochap'\">nochap</error>}\n")
    }

    fun test章跨ぎ参照で章が存在しない場合は章のみエラーになる() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - a.re\n")
        check("@<img>{<error descr=\"Cannot resolve 'nochap'\">nochap</error>|fig}\n")
    }

    fun test閉じられていないブロックは命令名がエラーになる() {
        myFixture.configureByText("a.re", "//note{\nfoo\n")
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
            .filter { it.description == "Block is not closed with //}" }
        assertEquals(listOf("//note"), errors.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    fun test参照されていない脚注は警告になる() {
        check("//footnote[<warning descr=\"Footnote 'fn1' is not referenced by @<fn>\">fn1</warning>][note]\n")
    }

    fun test1つの脚注を複数のfnで参照すると警告になる() {
        val descr = "Footnote 'fn1' is referenced by multiple @<fn> (duplicate ids in EPUB)"
        check(
            "a@<fn>{<warning descr=\"$descr\">fn1</warning>}b@<fn>{<warning descr=\"$descr\">fn1</warning>}\n" +
                "//footnote[fn1][note]\n"
        )
    }

    fun testlistとimageでIDが重複すると警告になる() {
        val descr = "ID 'x' is shared by multiple list/image/table blocks (duplicate ids in EPUB)"
        check(
            "//list[<warning descr=\"$descr\">x</warning>][cap]{\nfoo\n//}\n" +
                "//image[<warning descr=\"$descr\">x</warning>][cap]\n"
        )
    }

    fun testimgtable単体ではID重複の警告にならない() {
        check("//imgtable[t1][cap]{\nfoo\n//}\n")
    }

    fun testhrefに入っていないURLは警告になる() {
        val descr = "URL not wrapped in @<href> is not linked in EPUB"
        check(
            "see <warning descr=\"$descr\">https://example.com/a</warning>を参照\n" +
                "@<href>{https://example.com/b}\n" +
                "//note[cap]{\n<warning descr=\"$descr\">https://example.com/c</warning>\n//}\n" +
                "//emlist[cap]{\nhttps://example.com/d\n//}\n"
        )
    }
}
