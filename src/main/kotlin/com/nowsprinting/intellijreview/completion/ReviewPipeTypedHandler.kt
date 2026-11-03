package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.nowsprinting.intellijreview.psi.ReviewFile

// "|"の入力位置が参照命令の中かどうかはここで判定しない。対象外の位置では
// ReviewReferenceIdCompletionProviderが候補を返さず、ポップアップは開かないため。
class ReviewPipeTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped != '|' || file !is ReviewFile) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }
}
