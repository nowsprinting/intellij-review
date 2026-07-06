package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

// Re:VIEW本体(compile_headlineの正規表現とcolumn/nonum/notoc/nodispの各builderメソッド)を
// deepwiki経由で調査し確定した、見出し行の"[...]"に指定できる4種のオプション。
object ReviewHeadingOptionCompletionProvider : CompletionProvider<CompletionParameters>() {
    private val OPTIONS = listOf("column", "nonum", "notoc", "nodisp")

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        for (option in OPTIONS) {
            result.addElement(LookupElementBuilder.create(option))
        }
    }
}
