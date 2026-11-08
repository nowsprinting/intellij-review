package com.nowsprinting.intellijreview.preview

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
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
import java.awt.Point
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
    private var textEditor: Editor? = null

    // 片方を合わせると相手のリスナーも同期的に発火するため、追随中は相手からの追随を止めて往復させない
    private var syncing = false

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
            .finishOnUiThread(ModalityState.any()) { content ->
                if (content == null) return@finishOnUiThread
                panel.content = content
                // 描き直しで行の位置が変わるため、エディタの見えている位置に合わせ直す
                component.validate()
                followEditor()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    // エディタとプレビューのスクロールを互いに追随させる
    fun followScrolling(editor: Editor) {
        textEditor = editor
        editor.scrollingModel.addVisibleAreaListener({ if (it.newRectangle.y != it.oldRectangle?.y) followEditor() }, this)
        // ビューポートは描き直しでビューの高さが変わっただけでも発火するため、位置が変わったときだけ追随させる
        var lastY = component.viewport.viewPosition.y
        component.viewport.addChangeListener {
            val y = component.viewport.viewPosition.y
            if (y == lastY) return@addChangeListener
            lastY = y
            followPreview(y)
        }
    }

    private fun followEditor() {
        val editor = textEditor ?: return
        if (syncing || editor.document.lineCount == 0) return
        val line = editor.xyToLogicalPosition(Point(0, editor.scrollingModel.visibleArea.y)).line
        val offset = editor.document.getLineStartOffset(line.coerceIn(0, editor.document.lineCount - 1))
        val y = panel.yOf(offset) ?: return
        syncing = true
        try {
            // スクロールバーの値はビューの範囲に丸められるため、末尾付近でもはみ出さない
            component.verticalScrollBar.value = y
        } finally {
            syncing = false
        }
    }

    private fun followPreview(y: Int) {
        val editor = textEditor ?: return
        if (syncing) return
        val offset = panel.offsetAt(y) ?: return
        syncing = true
        try {
            editor.scrollingModel.disableAnimation()
            editor.scrollingModel.scrollVertically(editor.offsetToXY(offset).y)
        } finally {
            editor.scrollingModel.enableAnimation()
            syncing = false
        }
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
    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
        (secondEditor as ReviewPreviewFileEditor).followScrolling(firstEditor.editor)
        return TextEditorWithPreview(firstEditor, secondEditor, ReviewBundle.message("preview.splitEditorName"))
    }
}
