package com.nowsprinting.intellijreview.lang

import com.intellij.openapi.util.IconLoader

object ReviewIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/reviewFile.svg", ReviewIcons::class.java)

    @JvmField
    val COLUMN = IconLoader.getIcon("/icons/reviewColumn.svg", ReviewIcons::class.java)
}
