package com.nowsprinting.intellijreview.reference

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import com.nowsprinting.intellijreview.declaration.ReviewBookLayout
import org.jetbrains.yaml.psi.YAMLScalar

class ReviewCatalogReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java)
                .inFile(PlatformPatterns.psiFile().withName("catalog.yml")),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val scalar = element as YAMLScalar
                    val value = scalar.textValue
                    if (!value.endsWith(".re")) return PsiReference.EMPTY_ARRAY
                    val start = ElementManipulators.getValueTextRange(scalar).startOffset
                    val referenceSet = object : FileReferenceSet(value, scalar, start, this, true) {
                        override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
                            val catalogFile = scalar.containingFile.originalFile.virtualFile ?: return emptyList()
                            val dir = ReviewBookLayout.chapterDir(scalar.project, catalogFile) ?: return emptyList()
                            return listOfNotNull(scalar.manager.findDirectory(dir))
                        }
                    }
                    return arrayOf(*referenceSet.allReferences)
                }
            }
        )
    }
}
