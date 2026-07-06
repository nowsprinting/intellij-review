package com.nowsprinting.intellijreview.declaration

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

data class ReviewDeclaration(
    val id: String,
    val kind: ReviewDeclarationKind,
    val file: PsiFile,
    val element: PsiElement,
)
