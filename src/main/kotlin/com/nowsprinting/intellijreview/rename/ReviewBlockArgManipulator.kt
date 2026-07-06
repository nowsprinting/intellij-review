package com.nowsprinting.intellijreview.rename

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.nowsprinting.intellijreview.psi.ReviewElementFactory
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin

// //list[id][caption]{...}のようなブロック命令行のうち、1個目の"[...]"(ID引数)だけを
// 書き換える。ReviewBlockCommandMixin.setName()から使われるほか、ElementManipulators経由の
// 汎用コンテンツ変更にも対応する。
class ReviewBlockArgManipulator : AbstractElementManipulator<ReviewBlockCommandMixin>() {
    override fun handleContentChange(
        element: ReviewBlockCommandMixin,
        range: TextRange,
        newContent: String,
    ): ReviewBlockCommandMixin {
        val oldText = element.text
        val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
        val newElement = ReviewElementFactory.createBlockCommand(element.project, newText)
        return element.replace(newElement) as ReviewBlockCommandMixin
    }

    override fun getRangeInElement(element: ReviewBlockCommandMixin): TextRange {
        val identifier = element.nameIdentifier ?: return TextRange(0, 0)
        return TextRange(identifier.startOffsetInParent, identifier.startOffsetInParent + identifier.textLength)
    }
}
