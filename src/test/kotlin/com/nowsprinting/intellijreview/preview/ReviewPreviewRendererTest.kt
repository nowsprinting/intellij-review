package com.nowsprinting.intellijreview.preview

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Figure
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Line
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Placed
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ReviewPreviewRendererTest : BasePlatformTestCase() {

    private fun placedPages(text: String): List<List<Placed>> =
        ReviewPreviewRenderer(myFixture.configureByText("ch02.re", text)).paginate()

    private fun pages(text: String): List<List<String>> =
        placedPages(text).map { page -> page.mapNotNull { (it.item as? Line)?.text } }

    private fun addPng(path: String, width: Int, height: Int) {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }
        val file = myFixture.tempDirFixture.createFile(path)
        runWriteAction { file.setBinaryContent(bytes.toByteArray()) }
    }

    private fun setUpBook(options: String) {
        myFixture.addFileToProject("config.yml", "texdocumentclass: [\"review-jsbook\", \"$options\"]\n")
        myFixture.addFileToProject("catalog.yml", "PREDEF:\n  - pre.re\nCHAPS:\n  - ch01.re\n  - ch02.re\nAPPENDIX:\n  - app.re\n")
        myFixture.addFileToProject("ch01.re", "= はじめに\n\n//list[hello][挨拶]{\nputs 1\n//}\n")
        myFixture.addFileToProject("app.re", "= 補足\n")
        myFixture.addFileToProject("pre.re", "= まえがき\n")
    }

    fun test段落は字数で折り返され段落頭は全角1字下がる() {
        setUpBook("line_length=10zw,number_of_lines=30")

        val lines = pages("あいうえおかきくけこさしすせそ\nたちつてと\n").single()

        assertEquals(listOf("　あいうえおかきくけ", "こさしすせそたちつて", "と"), lines)
    }

    fun test行数を超えると改ページされる() {
        setUpBook("line_length=10zw,number_of_lines=2")

        val result = pages("あ\n\nい\n\nう\n")

        assertEquals(listOf(listOf("　あ", "　い"), listOf("　う")), result)
    }

    fun test行頭禁則文字は前の行にぶら下がる() {
        setUpBook("line_length=5zw,number_of_lines=30")

        val lines = pages("//noindent\nあいうえお。かき\n").single()

        assertEquals(listOf("あいうえお。", "かき"), lines)
    }

    fun test英単語は途中で分割されない() {
        setUpBook("line_length=3zw,number_of_lines=30")

        val lines = pages("//noindent\nab cdef\n").single()

        assertEquals(listOf("ab", "cdef"), lines)
    }

    fun test章参照には章番号と参照先の章タイトルが埋め込まれる() {
        setUpBook("line_length=40zw,number_of_lines=30")

        val lines = pages("//noindent\n@<chapref>{ch01}、@<chap>{app}、@<title>{pre}、@<chapref>{pre}\n").single()

        assertEquals(listOf("第1章「はじめに」、付録A、まえがき、「まえがき」"), lines)
    }

    fun test図表参照には章番号付きの通し番号が埋め込まれる() {
        setUpBook("line_length=40zw,number_of_lines=30")

        val lines = pages("//noindent\n@<list>{ch01|hello}と@<list>{a}\n\n//list[a][例]{\nx\n//}\n").single()

        assertEquals(listOf("リスト1.1とリスト2.1", "リスト2.1: 例", "x"), lines)
    }

    fun test見出し参照には節番号と見出しが埋め込まれる() {
        setUpBook("line_length=40zw,number_of_lines=30")

        val lines = pages("== 準備\n=={practice} 実践\n//noindent\n@<hd>{practice}\n")
            .single().filter { it.isNotEmpty() }

        assertEquals(listOf("2.1　準備", "2.2　実践", "「2.2 実践」"), lines)
    }

    fun test章見出しは改ページして章番号とタイトルを置く() {
        setUpBook("line_length=40zw,number_of_lines=30")

        val page = placedPages("= 本題\n").single()

        // 前アキ2行、章番号(\huge・行送り1.5行)、1行アキ、章タイトル
        assertEquals(listOf(2.0 to "第2章", 4.5 to "本題"), page.map { it.y to (it.item as Line).text })
        assertEquals(listOf(ReviewPageLayout.Style.CHAPTER_NUMBER, ReviewPageLayout.Style.CHAPTER_TITLE), page.map { (it.item as Line).style })
    }

    fun test節見出しは前に1行後に半行のアキを取る() {
        setUpBook("line_length=40zw,number_of_lines=30")

        val page = placedPages("//noindent\nあ\n\n== 節\n\n//noindent\nい\n").single()

        // 節見出し(\Large)の行送りは1.08行
        assertEquals(listOf(0.0, 2.0, 3.58), page.map { it.y }.map { Math.round(it * 100) / 100.0 })
        assertEquals(ReviewPageLayout.Style.SECTION, (page[1].item as Line).style)
    }

    fun testページに収まらない図は次のページへ送られる() {
        setUpBook("line_length=40zw,number_of_lines=10")

        // 本文3行のあとに、画像が見つからないときの8行ぶんの図
        val result = placedPages("あ\n\nい\n\nう\n\n//indepimage[none]\n")

        assertEquals(2, result.size)
        assertTrue(result[1].first().item is Figure)
    }

    fun test画像はscale指定で版面の幅に対する比の大きさになる() {
        setUpBook("line_length=40zw,number_of_lines=30")
        addPng("images/ch02/fig.png", 200, 100)

        val figure = placedPages("//image[fig][図][scale=0.5]\n").single().firstNotNullOf { it.item as? Figure }

        assertEquals(0.5, figure.widthRatio, 1e-9)
        // 版面の幅40zw(369.88pt)の半分で、縦横比1:2の高さを行送り16ptで割った行数
        assertEquals(5.78, figure.height, 0.01)
        assertNotNull(figure.image)
    }

    fun test読めない形式の画像は同名の読める画像で代用される() {
        setUpBook("line_length=40zw,number_of_lines=30")
        myFixture.addFileToProject("images/ch02/fig.pdf", "%PDF-1.4 dummy")
        addPng("images/ch02/fig.png", 200, 100)

        val figure = placedPages("//image[fig][図][scale=0.5]\n").single().firstNotNullOf { it.item as? Figure }

        assertNotNull(figure.image)
        assertEquals(5.78, figure.height, 0.01)
    }

    fun test画像はscale指定が無ければ72dpiとみなした実寸で置かれる() {
        setUpBook("line_length=40zw,number_of_lines=30")
        addPng("images/fig.png", 200, 100)

        val figure = placedPages("//image[fig][図]\n").single().firstNotNullOf { it.item as? Figure }

        assertEquals(200 * 72.27 / 72 / (40 * 9.2469), figure.widthRatio, 1e-3)
        assertEquals(100 * 72.27 / 72 / 16, figure.height, 1e-3)
    }

    fun testconfig_ymlが無ければreview_jsbookの既定値_A4で40字40行になる() {
        val settings = ReviewPageSettings.from(null)

        assertEquals(40, settings.charsPerLine)
        assertEquals(40, settings.linesPerPage)
    }

    fun testRe_VIEW2系のQ_W_Lオプションも読める() {
        val settings = ReviewPageSettings.from(listOf("review-jsbook", "Q=13,W=35,L=30"))

        assertEquals(35, settings.charsPerLine)
        assertEquals(30, settings.linesPerPage)
    }
}
