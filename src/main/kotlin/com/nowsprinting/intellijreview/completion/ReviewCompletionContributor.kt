package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.nowsprinting.intellijreview.psi.ReviewTypes

class ReviewCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement(ReviewTypes.BLOCK_COMMAND_NAME), ReviewBlockCommandCompletionProvider)
        extend(CompletionType.BASIC, psiElement(ReviewTypes.INLINE_NAME), ReviewInlineCommandCompletionProvider)
        extend(CompletionType.BASIC, psiElement(ReviewTypes.HEADING_OPTION), ReviewHeadingOptionCompletionProvider)
        extend(CompletionType.BASIC, psiElement(ReviewTypes.INLINE_CONTENT), ReviewReferenceIdCompletionProvider)
    }

    // BLOCK_COMMAND_NAME("//"+[a-z0-9]+)は小文字/数字のみを受理するため、既定のダミー識別子
    // ("IntellijIdeaRulezzz")には大文字が含まれ、補完トリガー時にそのトークンとして
    // 成立しない(TEXTにフォールバックしパターンが一致しなくなる)。全パターン共通で
    // 小文字のダミー識別子に差し替えることで解決する。
    override fun beforeCompletion(context: CompletionInitializationContext) {
        context.dummyIdentifier = "x"
    }
}
