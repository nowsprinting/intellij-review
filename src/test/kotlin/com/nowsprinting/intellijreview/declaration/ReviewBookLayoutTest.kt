package com.nowsprinting.intellijreview.declaration

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewBookLayoutTest : BasePlatformTestCase() {
    fun testコンテンツルートより上のconfig_ymlはベースディレクトリとみなさない() {
        val outsideDir = myFixture.tempDirFixture.getFile("")!!.parent
        val outerConfig = runWriteAction { outsideDir.createChildData(this, "config.yml") }
        try {
            val file = myFixture.addFileToProject("src/a.re", "= A\n")

            assertEquals(file.virtualFile.parent, ReviewBookLayout.baseDir(project, file.virtualFile))
        } finally {
            runWriteAction { outerConfig.delete(this) }
        }
    }

    fun testコンテンツルートのconfig_ymlはベースディレクトリとみなす() {
        myFixture.addFileToProject("config.yml", "contentdir: src\n")
        val file = myFixture.addFileToProject("src/a.re", "= A\n")

        assertEquals(file.virtualFile.parent.parent, ReviewBookLayout.baseDir(project, file.virtualFile))
    }
}
