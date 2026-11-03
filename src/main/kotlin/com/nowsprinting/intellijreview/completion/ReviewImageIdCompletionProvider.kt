package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin

// Re:VIEW本体は"images/<chapid>-<id>.ext"やビルダー別サブディレクトリも探索するが、
// 候補の出所は"images/<chapid>/"配下に限定する(ディレクトリ分けの構成に合わせた要件)。
object ReviewImageIdCompletionProvider : CompletionProvider<CompletionParameters>() {
    private val IMAGE_COMMANDS = setOf("image", "indepimage", "numberlessimage")

    // Re:VIEW本体のBook::Base#image_typesと同じ拡張子集合。
    // 拡張子で絞らないと.DS_Store等の非画像ファイルまで候補に出る。
    private val IMAGE_EXTENSIONS = setOf("ai", "psd", "eps", "pdf", "tif", "tiff", "png", "bmp", "jpg", "jpeg", "gif", "svg")

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        val block = position.parent as? ReviewBlockCommandMixin ?: return
        if (block.getCommandName() !in IMAGE_COMMANDS || block.nameIdentifier != position) return

        val reFile = parameters.originalFile.virtualFile ?: return
        val imageDir = reFile.parent?.findChild("images")?.findChild(reFile.nameWithoutExtension) ?: return
        val ids = imageDir.children
            .filter { !it.isDirectory && it.extension?.lowercase() in IMAGE_EXTENSIONS }
            .map { it.nameWithoutExtension }
            .distinct()

        val typedSoFar = position.text.substring(0, parameters.offset - position.textRange.startOffset)
        result.withPrefixMatcher(typedSoFar).addAllElements(ids.map { LookupElementBuilder.create(it) })
    }
}
