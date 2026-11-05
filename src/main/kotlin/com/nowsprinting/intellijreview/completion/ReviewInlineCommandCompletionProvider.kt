package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.ProcessingContext

object ReviewInlineCommandCompletionProvider : CompletionProvider<CompletionParameters>() {
    // 行頭アンカーで"#"コメント行の定義を除外する。Rubyを構文解析しないのは、
    // review-ext.rbでのdefinlineはモジュール直下に1行で書く慣習が定着しているため。
    private val DEFINLINE = Regex("""^\s*(?:ReVIEW::)?Compiler\.definline\s*\(?\s*:(\w+)""", RegexOption.MULTILINE)

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        for (name in (ReviewCommandCatalog.INLINE_COMMANDS + userDefinedInlineCommands(parameters)).distinct()) {
            result.addElement(LookupElementBuilder.create(name).withInsertHandler(inlineCommandInsertHandler))
        }
    }

    // Re:VIEWはreview-ext.rbをbookのベースディレクトリから読む。.reファイルと同じディレクトリに
    // 置く構成(articles/配下)を前提とし、images/探索(ReviewImageIdCompletionProvider)と揃えた。
    // VfsUtilCore.loadTextでなくDocument経由で読むのは、エディタで編集中の未保存の定義も拾うため。
    private fun userDefinedInlineCommands(parameters: CompletionParameters): List<String> {
        val extFile = parameters.originalFile.virtualFile?.parent?.findChild("review-ext.rb") ?: return emptyList()
        val text = FileDocumentManager.getInstance().getDocument(extFile)?.text ?: return emptyList()
        return DEFINLINE.findAll(text).map { it.groupValues[1] }.toList()
    }
}
