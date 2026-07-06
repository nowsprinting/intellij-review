package com.nowsprinting.intellijreview.highlighter

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.nowsprinting.intellijreview.lexer.ReviewLexerAdapter
import com.nowsprinting.intellijreview.psi.ReviewTypes

class ReviewSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = ReviewLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(KEYS[tokenType])

    companion object {
        val HEADING: TextAttributesKey =
            createTextAttributesKey("REVIEW_HEADING", DefaultLanguageHighlighterColors.KEYWORD)
        val HEADING_OPTION: TextAttributesKey =
            createTextAttributesKey("REVIEW_HEADING_OPTION", DefaultLanguageHighlighterColors.METADATA)
        val HEADING_LABEL: TextAttributesKey =
            createTextAttributesKey("REVIEW_HEADING_LABEL", DefaultLanguageHighlighterColors.LABEL)
        val BLOCK_COMMAND: TextAttributesKey =
            createTextAttributesKey("REVIEW_BLOCK_COMMAND", DefaultLanguageHighlighterColors.KEYWORD)
        val BLOCK_BRACKETS: TextAttributesKey =
            createTextAttributesKey("REVIEW_BLOCK_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val BLOCK_ARG: TextAttributesKey =
            createTextAttributesKey("REVIEW_BLOCK_ARG", DefaultLanguageHighlighterColors.PARAMETER)
        val RAW_BODY: TextAttributesKey =
            createTextAttributesKey("REVIEW_RAW_BODY", DefaultLanguageHighlighterColors.STRING)
        val INLINE_COMMAND: TextAttributesKey =
            createTextAttributesKey("REVIEW_INLINE_COMMAND", DefaultLanguageHighlighterColors.MARKUP_TAG)
        val INLINE_CONTENT: TextAttributesKey =
            createTextAttributesKey("REVIEW_INLINE_CONTENT", DefaultLanguageHighlighterColors.MARKUP_ENTITY)
        val LIST_MARKER: TextAttributesKey =
            createTextAttributesKey("REVIEW_LIST_MARKER", DefaultLanguageHighlighterColors.KEYWORD)
        val COMMENT: TextAttributesKey =
            createTextAttributesKey("REVIEW_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

        // LBRACKET/RBRACKETは見出しオプションとブロック引数の両方で共有されるトークンであり、
        // レクサ由来のトークン種別だけでは出現文脈(見出し行かブロック命令行か)を区別できない
        // (PSI構造を見ないSyntaxHighlighterBaseの制約上の割り切り。区別が必要ならAnnotatorで対応)。
        private val KEYS: Map<IElementType, TextAttributesKey> = buildMap {
            put(ReviewTypes.HEADING_MARKER, HEADING)
            put(ReviewTypes.HEADING_OPTION, HEADING_OPTION)
            put(ReviewTypes.HEADING_LABEL_OPEN, HEADING_LABEL)
            put(ReviewTypes.HEADING_LABEL_ID, HEADING_LABEL)
            put(ReviewTypes.HEADING_LABEL_CLOSE, HEADING_LABEL)
            put(ReviewTypes.BLOCK_COMMAND_NAME, BLOCK_COMMAND)
            put(ReviewTypes.LBRACKET, BLOCK_BRACKETS)
            put(ReviewTypes.RBRACKET, BLOCK_BRACKETS)
            put(ReviewTypes.BLOCK_BODY_LBRACE, BLOCK_BRACKETS)
            put(ReviewTypes.BLOCK_END, BLOCK_BRACKETS)
            put(ReviewTypes.BLOCK_ARG_TEXT, BLOCK_ARG)
            put(ReviewTypes.RAW_TEXT, RAW_BODY)
            put(ReviewTypes.INLINE_START, INLINE_COMMAND)
            put(ReviewTypes.INLINE_NAME, INLINE_COMMAND)
            put(ReviewTypes.INLINE_TAG_CLOSE, INLINE_COMMAND)
            put(ReviewTypes.INLINE_LBRACE, INLINE_CONTENT)
            put(ReviewTypes.INLINE_RBRACE, INLINE_CONTENT)
            put(ReviewTypes.INLINE_FENCE, INLINE_CONTENT)
            put(ReviewTypes.INLINE_CONTENT, INLINE_CONTENT)
            put(ReviewTypes.UL_MARKER, LIST_MARKER)
            put(ReviewTypes.OL_MARKER, LIST_MARKER)
            put(ReviewTypes.DL_MARKER, LIST_MARKER)
            put(ReviewTypes.LINE_COMMENT, COMMENT)
        }
    }
}
