package com.nowsprinting.intellijreview.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceBase
import com.nowsprinting.intellijreview.declaration.ReviewDeclaration
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind

// heading系(@<hd>/@<secref>/@<sec>/@<sectitle>)の参照。ブロックID系(ReviewBlockIdReference)と
// 異なり、"|"は「章跨ぎ("chapID|Label")」と「同一章内のネストパス("Sec|Subsec")」の両方の
// 意味を持ち、字句だけでは静的に判別できない(deepwikiでinline_hdの実装を確認: 先頭セグメントを
// 章IDとして解決を試み、実在する章が見つかった場合のみ章跨ぎとして扱う。見つからなければ
// "|"を含む文字列全体を現在の章内のitem_id(ネストパス)としてそのまま照合する)。そのため
// ReviewBlockIdReferenceのような機械的な最初の"|"分割・複数参照化はできず、単一の参照が
// 生テキスト全体を範囲として持ち、resolve()内で動的に判定する設計とした。
class ReviewHeadingReference(
    element: PsiElement,
    range: TextRange,
    private val rawId: String,
    private val kind: ReviewDeclarationKind,
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val pipeIndex = rawId.indexOf('|')
        if (pipeIndex >= 0) {
            val candidateChapterId = rawId.substring(0, pipeIndex)
            val chapterFile = ReviewDeclarationCache.byKind(project, ReviewDeclarationKind.CHAPTER)
                .find { it.id == candidateChapterId }?.file
            if (chapterFile != null) {
                return find(ReviewDeclarationCache.byKind(project, kind), chapterFile, rawId.substring(pipeIndex + 1))?.element
            }
        }
        return find(ReviewDeclarationCache.byKind(project, kind), element.containingFile, rawId)?.element
    }

    companion object {
        // Re:VIEW本体のIndex#[]と同じく、完全一致がなければ"|"区切りのいずれかのセグメントが
        // 一致する宣言を探し、複数あれば曖昧として解決しない。
        fun find(declarations: List<ReviewDeclaration>, file: PsiFile, itemId: String): ReviewDeclaration? {
            val inFile = declarations.filter { it.file == file }
            inFile.find { it.id == itemId }?.let { return it }
            return inFile.filter { itemId in it.id.split('|') }.singleOrNull()
        }
    }
}
