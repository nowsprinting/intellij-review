package com.nowsprinting.intellijreview.highlighter

import com.intellij.lang.Commenter

class ReviewCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "#@# "
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
