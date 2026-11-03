package com.nowsprinting.intellijreview.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.nowsprinting.intellijreview.ReviewBundle
import com.nowsprinting.intellijreview.lang.ReviewIcons
import javax.swing.Icon

class ReviewColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = ReviewIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = ReviewSyntaxHighlighter()
    override fun getDemoText(): String = DEMO_TEXT
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = ReviewBundle.message("colorSettings.review.displayName")

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor(ReviewBundle.message("colorSettings.review.heading"), ReviewSyntaxHighlighter.HEADING),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.headingOption"),
                ReviewSyntaxHighlighter.HEADING_OPTION
            ),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.headingLabel"),
                ReviewSyntaxHighlighter.HEADING_LABEL
            ),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.blockCommand"),
                ReviewSyntaxHighlighter.BLOCK_COMMAND
            ),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.blockBrackets"),
                ReviewSyntaxHighlighter.BLOCK_BRACKETS
            ),
            AttributesDescriptor(ReviewBundle.message("colorSettings.review.blockArg"), ReviewSyntaxHighlighter.BLOCK_ARG),
            AttributesDescriptor(ReviewBundle.message("colorSettings.review.rawBody"), ReviewSyntaxHighlighter.RAW_BODY),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.inlineCommand"),
                ReviewSyntaxHighlighter.INLINE_COMMAND
            ),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.inlineContent"),
                ReviewSyntaxHighlighter.INLINE_CONTENT
            ),
            AttributesDescriptor(
                ReviewBundle.message("colorSettings.review.listMarker"),
                ReviewSyntaxHighlighter.LIST_MARKER
            ),
            AttributesDescriptor(ReviewBundle.message("colorSettings.review.comment"), ReviewSyntaxHighlighter.COMMENT),
        )

        private val DEMO_TEXT = """
            = Chapter title

            //list[sample][caption]{
            body text
            //}

            Inline @<b>{bold} text.

             * list item
             1. numbered item

            #@# comment
        """.trimIndent()
    }
}
