package com.nowsprinting.intellijreview.psi

import com.intellij.psi.tree.IElementType
import com.nowsprinting.intellijreview.lang.ReviewLanguage

// 生成パーサーのエラー文("X or Y expected, got 'z'")はトークン型のtoString()をそのまま埋め込み、
// 文言を差し替えるフックを持たないため、toString()自体を執筆者が読める表記にする。
// 英字で始まらない表記はプラットフォームが'...'で囲むため、記号単体のトークンは引用符を付けずに返す。
// toString()はPSI/レクサのデバッグ出力にも使われるので、表記はトークンごとに区別できるものにする。
class ReviewTokenType(debugName: String) : IElementType(debugName, ReviewLanguage) {
    override fun toString(): String = DESCRIPTIONS[debugName] ?: debugName

    companion object {
        private val DESCRIPTIONS = mapOf(
            "HEADING_MARKER" to "heading marker '='",
            "HEADING_OPTION" to "heading option",
            "HEADING_LABEL_OPEN" to "heading label '{'",
            "HEADING_LABEL_ID" to "heading label",
            "HEADING_LABEL_CLOSE" to "heading label '}'",
            "BLOCK_COMMAND_NAME" to "block command name",
            "LBRACKET" to "[",
            "RBRACKET" to "]",
            "BLOCK_ARG_TEXT" to "block argument",
            "BLOCK_BODY_LBRACE" to "block body '{'",
            "BLOCK_END" to "//}",
            "INLINE_START" to "@<",
            "INLINE_NAME" to "inline command name",
            "INLINE_TAG_CLOSE" to ">",
            "INLINE_LBRACE" to "{",
            "INLINE_RBRACE" to "}",
            "INLINE_FENCE" to "inline fence '\$'/'|'",
            "INLINE_CONTENT" to "inline content",
            "UL_MARKER" to "list marker '*'",
            "OL_MARKER" to "list marker '1.'",
            "DL_MARKER" to "list marker ':'",
            "LINE_COMMENT" to "comment '#@#'",
            "TEXT" to "text",
            "RAW_TEXT" to "block body text",
            "EOL" to "end of line",
        )
    }
}
