package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement

// テンプレート中で最初に現れる"["か"{"の直後にキャレットを置く。プレースホルダ専用の記法は
// 導入せず、テンプレートの構造から機械的に決定できるようにした(シンプルさを優先)。
// 本文テンプレートは空行を持たないため、"{"直後の改行を飛ばすと"//}"の行頭に入ってしまう。
private fun caretOffsetWithin(template: String): Int {
    val bracket = template.indexOf('[')
    if (bracket >= 0) return bracket + 1
    val brace = template.indexOf('{')
    if (brace >= 0) return brace + 1
    return template.length
}

fun blockCommandInsertHandler(template: String): InsertHandler<LookupElement> =
    InsertHandler { context, _ ->
        if (template.isEmpty()) return@InsertHandler
        val tailOffset = context.tailOffset
        context.document.insertString(tailOffset, template)
        context.editor.caretModel.moveToOffset(tailOffset + caretOffsetWithin(template))
    }

// 計画書の設計どおり、命令名に関わらず一律">{}"を挿入しキャレットを波括弧内に置く。
val inlineCommandInsertHandler: InsertHandler<LookupElement> =
    InsertHandler { context, _ ->
        val tailOffset = context.tailOffset
        context.document.insertString(tailOffset, ">{}")
        context.editor.caretModel.moveToOffset(tailOffset + 2)
    }

// 既存の"ch01|id"上で章IDだけを選び直した場合に"|"が二重にならないよう、直後の"|"は再利用する。
val chapterDrillDownInsertHandler: InsertHandler<LookupElement> =
    InsertHandler { context, _ ->
        val tailOffset = context.tailOffset
        if (context.document.charsSequence.getOrNull(tailOffset) != '|') {
            context.document.insertString(tailOffset, "|")
        }
        context.editor.caretModel.moveToOffset(tailOffset + 1)
        AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
    }
