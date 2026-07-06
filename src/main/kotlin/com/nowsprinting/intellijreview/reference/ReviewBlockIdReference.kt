package com.nowsprinting.intellijreview.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind

// chapterIdがnullの場合は同一ファイル内、非nullの場合はそのchapterIdが指すファイル内のみを
// 探索する("chapterId|id"跨ぎ記法。@<fn>/@<endnote>はSAME_CHAPTER_ONLY_COMMANDSとして
// ReviewReferenceFactory側でそもそもchapterId!=nullの経路に到達させない)。
class ReviewBlockIdReference(
    element: PsiElement,
    range: TextRange,
    private val id: String,
    private val kind: ReviewDeclarationKind,
    private val chapterId: String?,
) : PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val candidates = ReviewDeclarationCache.byKind(project, kind)
        val scoped = if (chapterId != null) {
            val chapterFile = ReviewDeclarationCache.byKind(project, ReviewDeclarationKind.CHAPTER)
                .find { it.id == chapterId }?.file ?: return null
            candidates.filter { it.file == chapterFile }
        } else {
            candidates.filter { it.file == element.containingFile }
        }
        return scoped.find { it.id == id }?.element
    }
}
