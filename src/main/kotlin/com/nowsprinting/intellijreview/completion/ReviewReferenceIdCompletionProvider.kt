package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

object ReviewReferenceIdCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        val inlineCommand = position.parent as? ReviewInlineCommandMixin ?: return
        val commandName = inlineCommand.getCommandName()
        val kind = ReviewCommandCatalog.INLINE_REFERENCE_KINDS[commandName] ?: return

        val leafStart = position.textRange.startOffset
        val typedSoFar = position.text.substring(0, parameters.offset - leafStart)
        val project = position.project

        if (commandName in ReviewCommandCatalog.GLOBAL_LOOKUP_COMMANDS) {
            // @<bib>はbib.re一箇所の全体共有インデックスを参照するため、ファイル・章プレフィックスに
            // 関わらずプロジェクト全体のkind一致宣言をそのまま候補にする(ReviewCommandCatalog参照)。
            val candidates = ReviewDeclarationCache.byKind(project, kind)
            result.withPrefixMatcher(typedSoFar).addAllElements(candidates.map { LookupElementBuilder.create(it.id) })
            return
        }

        // 章をまたいだ参照は"chapterId|id"記法を使う(Re:VIEW本体のextract_chapter_id相当)。
        // SAME_CHAPTER_ONLY_COMMANDS(@<fn>・@<endnote>)はこの記法をサポートせず常に
        // 同一ファイル限定とする(計画書のStep 5/6テスト一覧「@<fn>同一ファイル限定」に対応)。
        val supportsCrossChapter = commandName !in ReviewCommandCatalog.SAME_CHAPTER_ONLY_COMMANDS
        val pipeIndex = typedSoFar.indexOf('|')

        // parameters.positionはダミー識別子挿入用に複製された非物理ファイル内の要素であり、
        // containingFileはReviewDeclarationCacheが走査する実ファイルとは別インスタンスになる。
        // 同一ファイル判定にはoriginalFile(複製前の実ファイル)を使う必要がある。
        val currentFile = parameters.originalFile

        if (pipeIndex >= 0 && supportsCrossChapter) {
            val chapterId = typedSoFar.substring(0, pipeIndex)
            val idTyped = typedSoFar.substring(pipeIndex + 1)
            val chapterDeclaration =
                ReviewDeclarationCache.byKind(project, ReviewDeclarationKind.CHAPTER).find { it.id == chapterId }
                    ?: return
            val candidates = ReviewDeclarationCache.byKind(project, kind)
                .filter { it.file == chapterDeclaration.file }
            result.withPrefixMatcher(idTyped).addAllElements(candidates.map { LookupElementBuilder.create(it.id) })
            return
        }

        val sameFileCandidates = ReviewDeclarationCache.byKind(project, kind)
            .filter { it.file == currentFile }
        val elements = sameFileCandidates.map { LookupElementBuilder.create(it.id) }.toMutableList()
        if (supportsCrossChapter) {
            // "|"を続けて入力すれば他章参照になることを示すヒントとして章IDも候補に加える。
            elements += ReviewDeclarationCache.byKind(project, ReviewDeclarationKind.CHAPTER)
                .map { LookupElementBuilder.create(it.id).withTypeText("chapter") }
        }
        result.withPrefixMatcher(typedSoFar).addAllElements(elements)
    }
}
