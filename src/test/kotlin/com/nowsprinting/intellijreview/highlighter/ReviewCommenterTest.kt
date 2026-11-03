package com.nowsprinting.intellijreview.highlighter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewCommenterTest : BasePlatformTestCase() {
    fun test行コメントをトグルすると先頭にシャープアットシャープと空白が付く() {
        myFixture.configureByText("sample.re", "Hello, Re:VIEW.<caret>\n")
        myFixture.performEditorAction("CommentByLineComment")
        myFixture.checkResult("#@# Hello, Re:VIEW.\n<caret>")
    }

    fun testコメント済みの行をトグルするとシャープアットシャープと空白が外れる() {
        myFixture.configureByText("sample.re", "#@# Hello, Re:VIEW.<caret>\n")
        myFixture.performEditorAction("CommentByLineComment")
        myFixture.checkResult("Hello, Re:VIEW.\n<caret>")
    }
}
