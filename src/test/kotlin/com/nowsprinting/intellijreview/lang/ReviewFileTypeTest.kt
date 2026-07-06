package com.nowsprinting.intellijreview.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.nowsprinting.intellijreview.psi.ReviewFile

class ReviewFileTypeTest : BasePlatformTestCase() {
    fun test拡張子reのファイルがReVIEWファイルタイプとして認識される() {
        val file = myFixture.configureByText("chapter1.re", "=Chapter 1\n")
        assertEquals(ReviewFileType, file.fileType)
        assertEquals(ReviewLanguage, file.language)
        assertTrue(file is ReviewFile)
    }
}
