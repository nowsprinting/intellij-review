package com.nowsprinting.intellijreview.rename

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import com.nowsprinting.intellijreview.reference.ReviewHeadingReference

// "@<hd>{Sec|Sub}"の解決先はSubなので、既定の参照検索ではSecのリネーム時にSecセグメントが
// 書き換え対象に入らない。ReviewReferenceFactoryで親セグメントにも常設の参照を張る案は、
// Ctrl+クリックやFind Usagesで1つの命令が複数候補を持つことになるため採らず、リネーム時だけ
// 子孫見出しへの参照から親セグメントの参照を作る。
class ReviewHeadingRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean = element is ReviewHeadingMixin

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> {
        val references = super.findReferences(element, searchScope, searchInCommentsAndStrings).toMutableList()
        val heading = element as ReviewHeadingMixin
        val declarations = ReviewDeclarationCache.byKind(heading.project, ReviewDeclarationKind.HEADING)
        val path = declarations.find { it.element == heading }?.id ?: return references
        val segmentStart = path.length - heading.name.length

        val descendants = declarations.filter { it.file == heading.containingFile && it.id.startsWith("$path|") }
        for (descendant in descendants) {
            for (reference in ReferencesSearch.search(descendant.element, searchScope)) {
                // パスを書かずに末尾セグメントだけで照合された参照("@<hd>{Sub}")には親セグメントが無い。
                if (reference !is ReviewHeadingReference || !reference.rawId.endsWith(descendant.id)) continue
                val start = reference.rangeInElement.startOffset + reference.rawId.length - descendant.id.length + segmentStart
                references += SegmentReference(reference.element, TextRange(start, start + heading.name.length), heading)
            }
        }
        return references
    }

    private class SegmentReference(element: PsiElement, range: TextRange, private val target: PsiElement) :
        PsiReferenceBase<PsiElement>(element, range) {
        override fun resolve(): PsiElement = target
    }
}
