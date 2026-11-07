package com.nowsprinting.intellijreview.preview

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.nowsprinting.intellijreview.ReviewBundle
import com.nowsprinting.intellijreview.lang.ReviewFileType
import java.beans.PropertyChangeListener
import javax.swing.JComponent

// JCEFでHTMLを描かずSwingで直接描くのは、字送りを行分割の計算(全角=1zw、半角=0.5zw)と
// 一致させるためと、JCEF非対応環境へのフォールバックを要らなくするため。
class ReviewPreviewFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    internal val panel = ReviewPreviewPanel()
    private val component = JBScrollPane(panel)
    private val images = ReviewPreviewImageCache()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    // coalesceByにthisを渡すと、UserDataHolderは他の処理と衝突しうるキーとしてプラットフォームに拒否される
    private val updateKey = Any()

    init {
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleUpdate()
        }, this)
        scheduleUpdate()
    }

    private fun scheduleUpdate() {
        alarm.cancelAllRequests()
        alarm.addRequest(::update, UPDATE_DELAY_MS)
    }

    private fun update() {
        ReadAction.nonBlocking<ReviewPreviewPanel.Content?> {
            PsiManager.getInstance(project).findFile(file)?.let {
                val renderer = ReviewPreviewRenderer(it, images)
                ReviewPreviewPanel.Content(renderer.settings, renderer.paginate())
            }
        }
            .inSmartMode(project)
            .withDocumentsCommitted(project)
            .expireWith(this)
            .coalesceBy(updateKey)
            .finishOnUiThread(ModalityState.any()) { content -> if (content != null) panel.content = content }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = ReviewBundle.message("preview.name")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid

    // 他の章のタイトルなど、このファイル以外の変更も参照の埋め込みに反映させる
    override fun selectNotify() = scheduleUpdate()

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit

    companion object {
        private const val UPDATE_DELAY_MS = 300L
    }
}

class ReviewPreviewFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file.fileType == ReviewFileType
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = ReviewPreviewFileEditor(project, file)
    override fun getEditorTypeId(): String = "review-preview-editor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

class ReviewSplitEditorProvider : TextEditorWithPreviewProvider(ReviewPreviewFileEditorProvider()), DumbAware {
    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor =
        TextEditorWithPreview(firstEditor, secondEditor, ReviewBundle.message("preview.splitEditorName"))
}
