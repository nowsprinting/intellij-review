package com.nowsprinting.intellijreview.structure

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.lang.ReviewIcons

class ReviewStructureViewTest : BasePlatformTestCase() {
    fun test見出しレベルに応じて階層ネストされる() {
        myFixture.configureByText(
            "structure_nesting.re",
            "=Chapter\n==Section A\n===Section A-1\n==Section B\n"
        )

        val roots = ReviewHeadingTreeBuilder.build(myFixture.file)

        val chapter = roots.single()
        assertEquals("Chapter", captionOf(chapter))
        assertEquals(2, chapter.children.size)
        val sectionA = chapter.children[0]
        val sectionB = chapter.children[1]
        assertEquals("Section A", captionOf(sectionA))
        assertEquals("Section B", captionOf(sectionB))
        assertEquals(1, sectionA.children.size)
        assertEquals("Section A-1", captionOf(sectionA.children[0]))
        assertEquals(0, sectionB.children.size)
    }

    fun testcolumn見出しは専用アイコンでその配下は暗黙に同位以上の見出しで閉じる() {
        myFixture.configureByText(
            "structure_column_implicit.re",
            "=Chapter\n==[column]Column Title\n===Inside Column\n==Section After\n"
        )

        val roots = ReviewHeadingTreeBuilder.build(myFixture.file)

        val chapter = roots.single()
        assertEquals(2, chapter.children.size)
        val column = chapter.children[0]
        val after = chapter.children[1]
        assertTrue(column.heading.isColumn())
        assertEquals(1, column.children.size)
        assertEquals("Inside Column", captionOf(column.children[0]))
        assertEquals("Section After", captionOf(after))

        val element = ReviewHeadingStructureViewElement(column)
        assertEquals(ReviewIcons.COLUMN, element.getIcon(false))
    }

    fun testcolumn見出しは明示的な終了マーカーで閉じる() {
        myFixture.configureByText(
            "structure_column_explicit.re",
            "=Chapter\n==[column]Column Title\n===Inside Column\n==[/column]\n===After Close\n"
        )

        val roots = ReviewHeadingTreeBuilder.build(myFixture.file)

        val chapter = roots.single()
        // "[/column]"見出し自体はツリーのノードを持たない(制御見出しのため)。
        assertEquals(2, chapter.children.size)
        val column = chapter.children[0]
        val afterClose = chapter.children[1]
        assertEquals(1, column.children.size)
        assertEquals("Inside Column", captionOf(column.children[0]))
        assertEquals("After Close", captionOf(afterClose))
    }

    fun testオプション付き見出しは通常どおりネストされアイコンを持たない() {
        myFixture.configureByText(
            "structure_heading_option.re",
            "=Chapter\n==[nonum]Section Without Number\n"
        )

        val roots = ReviewHeadingTreeBuilder.build(myFixture.file)

        val chapter = roots.single()
        val section = chapter.children.single()
        assertFalse(section.heading.isColumn())
        val element = ReviewHeadingStructureViewElement(section)
        assertNull(element.getIcon(false))
        assertEquals("Section Without Number", captionOf(section))
    }

    fun `test明示ラベルを持つ見出しはキャプションを表示しラベルはlocation stringになる`() {
        myFixture.configureByText(
            "structure_label.re",
            "=Chapter\n=={lbl1}Sub Title\n"
        )

        val roots = ReviewHeadingTreeBuilder.build(myFixture.file)

        val section = roots.single().children.single()
        val element = ReviewHeadingStructureViewElement(section)
        assertEquals("Sub Title", element.presentableText)
        assertEquals("lbl1", element.locationString)
    }

    fun testStructureViewBuilderがモデルを構築できる() {
        myFixture.configureByText("structure_builder.re", "=Chapter\n==Section\n")

        val builder = ReviewStructureViewFactory().getStructureViewBuilder(myFixture.file)

        assertNotNull(builder)
    }

    fun testファイルノードは表示されずchapter見出しがトップ要素になる() {
        myFixture.configureByText("structure_root_hidden.re", "=Chapter\n")

        val builder = ReviewStructureViewFactory().getStructureViewBuilder(myFixture.file)
            as com.intellij.ide.structureView.TreeBasedStructureViewBuilder

        assertFalse(builder.isRootNodeShown)
    }

    fun testファイルルート要素経由でも見出しツリーを取得できる() {
        myFixture.configureByText("structure_file_root.re", "=Chapter\n==Section\n")

        val fileElement = ReviewFileStructureViewElement(myFixture.file)
        val children = fileElement.children

        assertEquals(1, children.size)
        val chapterElement = children[0] as ReviewHeadingStructureViewElement
        assertEquals("Chapter", chapterElement.presentableText)
        assertEquals(1, chapterElement.children.size)
    }

    private fun captionOf(node: ReviewHeadingNode): String? = ReviewHeadingStructureViewElement(node).presentableText
}
