package com.nowsprinting.intellijreview.preview

import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReviewPreviewFileEditorTest : BasePlatformTestCase() {
    fun testプレビューペインに参考レンダリングが表示される() {
        myFixture.configureByText("a.re", "= 章\n\n本文\n")
        val editor = ReviewPreviewFileEditor(project, myFixture.file.virtualFile)
        Disposer.register(testRootDisposable, editor)
        val lines = { editor.panel.content?.pages.orEmpty().flatten().mapNotNull { (it.item as? ReviewPageLayout.Line)?.text } }

        PlatformTestUtil.waitWithEventsDispatching("プレビューが描画されない", { "　本文" in lines() }, 10)
    }

    // Quick Definition(ImplementationViewComponent)は先頭のプロバイダがQuickDefinitionProviderなら
    // 定義箇所のテキストを、そうでなければそのエディタ全体を表示する。
    fun test先頭のエディタプロバイダはQuickDefinitionで定義箇所を表示する() {
        myFixture.configureByText("a.re", "= 章\n")
        val providers = FileEditorProviderManager.getInstance().getProviderList(project, myFixture.file.virtualFile)

        assertTrue(providers.first() is QuickDefinitionProvider)
    }
}
