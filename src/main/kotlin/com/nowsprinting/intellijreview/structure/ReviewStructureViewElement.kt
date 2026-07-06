package com.nowsprinting.intellijreview.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.psi.PsiFile
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.lang.ReviewIcons
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import javax.swing.Icon

class ReviewFileStructureViewElement(psiFile: PsiFile) : PsiTreeElementBase<PsiFile>(psiFile) {
    override fun getPresentableText(): String? = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val file = element ?: return emptyList()
        return ReviewHeadingTreeBuilder.build(file).map { ReviewHeadingStructureViewElement(it) }
    }
}

class ReviewHeadingStructureViewElement(private val node: ReviewHeadingNode) :
    PsiTreeElementBase<ReviewHeadingMixin>(node.heading) {

    // 計画書の要求どおり、表示テキストは常にキャプション(明示ラベルがあっても)、
    // 明示ラベルはlocation string(右側の補助テキスト)として表示する。
    override fun getPresentableText(): String? = element?.let { ReviewDeclarationCache.normalizeCaption(it) }

    override fun getLocationString(): String? = element?.getLabel()

    override fun getIcon(open: Boolean): Icon? = if (element?.isColumn() == true) ReviewIcons.COLUMN else null

    override fun getChildrenBase(): Collection<StructureViewTreeElement> =
        node.children.map { ReviewHeadingStructureViewElement(it) }
}
