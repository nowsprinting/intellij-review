package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext

object ReviewBlockCommandCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        for (spec in ReviewCommandCatalog.blockCommands(parameters.originalFile)) {
            result.addElement(
                LookupElementBuilder.create(spec.name).withInsertHandler(blockCommandInsertHandler(spec.template))
            )
        }
    }
}
