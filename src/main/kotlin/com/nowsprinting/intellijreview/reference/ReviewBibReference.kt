package com.nowsprinting.intellijreview.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind

// bibpaperはbib.re一箇所にのみ定義されるbook全体で共有の宣言のため、現在のファイルに
// よらずプロジェクト全体のBIBPAPER宣言から探す(ReviewBlockIdReferenceの同一ファイル
// 限定ロジックとは独立させた別クラスとした)。
class ReviewBibReference(element: PsiElement, range: TextRange, private val id: String) :
    PsiReferenceBase<PsiElement>(element, range) {

    override fun resolve(): PsiElement? =
        ReviewDeclarationCache.byKind(element.project, ReviewDeclarationKind.BIBPAPER)
            .find { it.id == id }?.element
}
