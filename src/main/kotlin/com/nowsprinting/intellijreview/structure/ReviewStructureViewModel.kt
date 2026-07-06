package com.nowsprinting.intellijreview.structure

import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class ReviewStructureViewModel(psiFile: PsiFile, editor: Editor?) :
    StructureViewModelBase(psiFile, editor, ReviewFileStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    // 見出しの文書内出現順を保持するため、既定のアルファベット順ソート(Sorter.ALPHA_SORTER)は
    // 適用しない。
    override fun getSorters(): Array<Sorter> = emptyArray()

    override fun isAlwaysShowsPlus(element: com.intellij.ide.structureView.StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: com.intellij.ide.structureView.StructureViewTreeElement): Boolean =
        element.children.isEmpty()
}
