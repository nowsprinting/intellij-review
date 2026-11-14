package com.nowsprinting.intellijreview.rename

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

// ラベルの無い見出しはキャプションがリネーム対象で空白を含み得るため、言語単位の
// ReviewNamesValidatorでは判定できない。登録されたRenameInputValidatorはNamesValidatorより
// 優先されるので、ラベル付き見出しはReviewNamesValidatorへ委ねる。
class ReviewHeadingRenameInputValidator : RenameInputValidator {
    override fun getPattern(): ElementPattern<out PsiElement> = PlatformPatterns.psiElement(ReviewHeadingMixin::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
        if ((element as ReviewHeadingMixin).getLabel() != null) return ReviewNamesValidator().isIdentifier(newName, element.project)
        // "["/"{"は見出しオプション・ラベル、"@<"はインライン命令として字句解析され、
        // 前後の空白は正規化キャプション(trim済み)と一致しなくなる。
        return newName.isNotBlank() && newName == newName.trim() &&
            newName.none { it in "\r\n[]{}|" } && "@<" !in newName
    }
}
