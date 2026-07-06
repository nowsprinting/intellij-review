package com.nowsprinting.intellijreview.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.nowsprinting.intellijreview.lang.ReviewFileType
import com.nowsprinting.intellijreview.lang.ReviewLanguage

class ReviewFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ReviewLanguage) {
    override fun getFileType(): FileType = ReviewFileType
    override fun toString(): String = "Re:VIEW File"
}
