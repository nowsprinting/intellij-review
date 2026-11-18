package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.nowsprinting.intellijreview.declaration.ReviewBookLayout
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin

// Re:VIEW本体は"<imagedir>/<chapid>-<id>.ext"やビルダー別サブディレクトリも探索するが、
// 候補の出所は"<imagedir>/<chapid>/"配下に限定する(ディレクトリ分けの構成に合わせた要件)。
object ReviewImageIdCompletionProvider : CompletionProvider<CompletionParameters>() {
    private val IMAGE_COMMANDS = setOf("image", "indepimage", "numberlessimage")

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        val block = position.parent as? ReviewBlockCommandMixin ?: return
        if (block.getCommandName() !in IMAGE_COMMANDS || block.nameIdentifier != position) return

        val reFile = parameters.originalFile.virtualFile ?: return
        val imageDir = ReviewBookLayout.imageDir(position.project, reFile)?.findChild(reFile.nameWithoutExtension) ?: return
        // 拡張子で絞らないと.DS_Store等の非画像ファイルまで候補に出る。
        val ids = imageDir.children
            .filter { !it.isDirectory && it.extension?.lowercase() in ReviewBookLayout.IMAGE_EXTENSIONS }
            .map { it.nameWithoutExtension }
            .distinct()

        val typedSoFar = position.text.substring(0, parameters.offset - position.textRange.startOffset)
        result.withPrefixMatcher(typedSoFar).addAllElements(ids.map { LookupElementBuilder.create(it) })
    }
}
