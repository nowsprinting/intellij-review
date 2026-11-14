package com.nowsprinting.intellijreview.rename

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

// Re:VIEWのID・ラベルはハイフンやドットを含められる。未登録だと既定のJava識別子判定で
// "foo-bar"等が拒否される。拒否するのは定義側"[...]"/"{...}"や参照側"{...}"を壊す括弧、
// 章跨ぎ区切りの"|"、および空白。
class ReviewNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean = false

    override fun isIdentifier(name: String, project: Project?): Boolean =
        name.isNotEmpty() && name.none { it.isWhitespace() || it in "[]{}|" }
}
