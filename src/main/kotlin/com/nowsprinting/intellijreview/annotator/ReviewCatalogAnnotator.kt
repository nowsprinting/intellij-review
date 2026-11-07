package com.nowsprinting.intellijreview.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.nowsprinting.intellijreview.ReviewBundle
import org.jetbrains.yaml.psi.YAMLScalar

// YAMLプラグインは解決できない参照を赤表示しないため、catalog.ymlの章ファイル参照だけ自前で出す。
class ReviewCatalogAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is YAMLScalar || element.containingFile.name != "catalog.yml") return

        // "dir/a.re"でdirが無ければa.reも必ず解決できないため、先頭の未解決セグメントだけを報告する。
        val unresolved = element.references.filterIsInstance<FileReference>()
            .firstOrNull { it.multiResolve(false).isEmpty() } ?: return
        holder.newAnnotation(HighlightSeverity.ERROR, ReviewBundle.message("annotator.unresolvedReference", unresolved.canonicalText))
            .range(unresolved.rangeInElement.shiftRight(element.textRange.startOffset)).create()
    }
}
