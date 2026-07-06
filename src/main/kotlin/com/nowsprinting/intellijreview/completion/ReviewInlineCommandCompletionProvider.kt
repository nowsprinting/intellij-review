package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

object ReviewInlineCommandCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        for (name in ReviewCommandCatalog.INLINE_COMMANDS) {
            result.addElement(LookupElementBuilder.create(name).withInsertHandler(inlineCommandInsertHandler))
        }
    }
}
