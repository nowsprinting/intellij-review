package com.nowsprinting.intellijreview.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.lang.ReviewIcons
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import javax.swing.Icon

// PsiTreeElementBase (com.intellij.ide.structureView.impl.common) was split into the
// intellij.platform.structureView module by IntelliJ Platform 2026.2's modularization,
// requiring an extra dependency declaration. StructureViewTreeElement/ItemPresentation are
// stable public APIs unaffected by that split, so implementing them directly avoids needing
// any extra dependency declaration.
class ReviewFileStructureViewElement(private val psiFile: PsiFile) : StructureViewTreeElement, ItemPresentation {
    override fun getValue(): PsiFile = psiFile
    override fun getPresentation(): ItemPresentation = this
    override fun getPresentableText(): String? = psiFile.name
    override fun getLocationString(): String? = null
    override fun getIcon(unused: Boolean): Icon? = null

    override fun getChildren(): Array<TreeElement> =
        ReviewHeadingTreeBuilder.build(psiFile).map { ReviewHeadingStructureViewElement(it) as TreeElement }.toTypedArray()

    override fun navigate(requestFocus: Boolean) {
        (psiFile as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (psiFile as? NavigatablePsiElement)?.canNavigate() ?: false
    override fun canNavigateToSource(): Boolean = (psiFile as? NavigatablePsiElement)?.canNavigateToSource() ?: false
}

class ReviewHeadingStructureViewElement(private val node: ReviewHeadingNode) : StructureViewTreeElement, ItemPresentation {
    private val element: ReviewHeadingMixin get() = node.heading

    override fun getValue(): ReviewHeadingMixin = element
    override fun getPresentation(): ItemPresentation = this

    // Presentable text is always the caption, even when an explicit label is present;
    // the explicit label is shown as the location string (the auxiliary text on the right) instead.
    override fun getPresentableText(): String? = ReviewDeclarationCache.normalizeCaption(element)

    override fun getLocationString(): String? = element.getLabel()

    override fun getIcon(unused: Boolean): Icon? = if (element.isColumn()) ReviewIcons.COLUMN else null

    override fun getChildren(): Array<TreeElement> =
        node.children.map { ReviewHeadingStructureViewElement(it) as TreeElement }.toTypedArray()

    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? NavigatablePsiElement)?.canNavigate() ?: false
    override fun canNavigateToSource(): Boolean = (element as? NavigatablePsiElement)?.canNavigateToSource() ?: false
}
