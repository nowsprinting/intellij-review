package com.nowsprinting.intellijreview.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.nowsprinting.intellijreview.ReviewBundle
import javax.swing.Icon

object ReviewFileType : LanguageFileType(ReviewLanguage) {
    override fun getName(): String = "Re:VIEW"
    override fun getDescription(): String = ReviewBundle.message("filetype.review.description")
    override fun getDefaultExtension(): String = "re"
    override fun getIcon(): Icon = ReviewIcons.FILE
}
