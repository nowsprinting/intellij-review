package com.nowsprinting.intellijreview.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.nowsprinting.intellijreview.psi.ReviewFile
import com.nowsprinting.intellijreview.psi.ReviewTypes

class ReviewListEnterHandler : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
        if (file !is ReviewFile || editor !is EditorEx) return Result.Continue
        val document = editor.document
        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        if (line == 0) return Result.Continue
        val prevStart = document.getLineStartOffset(line - 1)

        // 正規表現だけで判定するとブロック本文中の " * " まで継続してしまうため、
        // 文脈を解釈済みのハイライタのトークン種別で判定する(PSIはEnter直後に未コミット)。
        val tokenType = editor.highlighter.createIterator(prevStart).tokenType
        if (tokenType != ReviewTypes.UL_MARKER && tokenType != ReviewTypes.OL_MARKER) return Result.Continue

        val prevLine = document.getText(TextRange(prevStart, document.getLineEndOffset(line - 1)))
        val match = MARKER.find(prevLine) ?: return Result.Continue
        val (indent, bullet, number, space) = match.destructured

        if (prevLine.isBlank() || prevLine.length == match.value.length) {
            // 空の項目でEnterしたらマーカーを消してリストを抜ける(Markdownと同じ挙動)
            document.deleteString(prevStart, caret)
            editor.caretModel.moveToOffset(prevStart)
        } else {
            val next = if (number.isEmpty()) bullet else "${number.toBigInteger().inc()}."
            val prefix = indent + next + space
            val lineStart = document.getLineStartOffset(line)
            document.replaceString(lineStart, caret, prefix)
            editor.caretModel.moveToOffset(lineStart + prefix.length)
        }
        return Result.Stop
    }

    private companion object {
        val MARKER = Regex("""^([ \t]+)(?:(\*+)|(\d+)\.)([ \t])[ \t]*""")
    }
}
