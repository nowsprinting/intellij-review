package com.nowsprinting.intellijreview.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester

class ReviewPipeTypedHandlerTest : BasePlatformTestCase() {
    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runInDispatchThread(): Boolean = false

    fun test章IDの後にパイプを入力すると当該章の画像の補完ポップアップが開く() {
        myFixture.addFileToProject("catalog.yml", "CHAPS:\n  - chapter1.re\n  - a.re\n")
        myFixture.addFileToProject("chapter1.re", "= Chapter 1\n//image[other1][caption]\n")
        myFixture.configureByText("a.re", "= A\n@<img>{chapter1<caret>}\n")
        tester.runWithAutoPopupEnabled {
            tester.typeWithPauses("|")
            assertEquals(listOf("other1"), tester.lookup?.items?.map { it.lookupString })
        }
    }
}
