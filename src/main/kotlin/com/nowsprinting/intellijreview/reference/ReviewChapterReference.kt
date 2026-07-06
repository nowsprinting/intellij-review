package com.nowsprinting.intellijreview.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind

class ReviewChapterReference(element: PsiElement, range: TextRange, private val chapterId: String) :
    PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? =
        ReviewDeclarationCache.byKind(element.project, ReviewDeclarationKind.CHAPTER)
            .find { it.id == chapterId }?.file

    // ファイルリネーム追随時、プラットフォームは新ファイル名(".re"拡張子付き)をそのまま渡してくる。
    // 章IDは拡張子を含まないため、置換前に取り除く。
    override fun handleElementRename(newElementName: String): PsiElement =
        super.handleElementRename(newElementName.removeSuffix(".re"))
}
