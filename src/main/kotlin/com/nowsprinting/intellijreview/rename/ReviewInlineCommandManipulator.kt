package com.nowsprinting.intellijreview.rename

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.nowsprinting.intellijreview.psi.ReviewElementFactory
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

// @<list>{id}のようなインライン命令の参照(usage)側。ReviewReferenceFactoryが構成する
// 各PsiReferenceのrangeInElement(内容の一部、"chapterId|id"分割時は各セグメント)だけを
// 書き換える。リネーム追随(rename-to-usages)はPsiReferenceBase.handleElementRename()の
// 既定実装からこのマニピュレータ経由で呼ばれる。
class ReviewInlineCommandManipulator : AbstractElementManipulator<ReviewInlineCommandMixin>() {
    override fun handleContentChange(
        element: ReviewInlineCommandMixin,
        range: TextRange,
        newContent: String,
    ): ReviewInlineCommandMixin {
        val oldText = element.text
        val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
        val newElement = ReviewElementFactory.createInlineCommand(element.project, "$newText\n")
        return element.replace(newElement) as ReviewInlineCommandMixin
    }
}
