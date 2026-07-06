package com.nowsprinting.intellijreview.completion

// templateは補完確定直後にInsertHandlerが挿入する引数の雛形。"|"はキャレット移動先を表す
// (実際のトークンには含まれない、テンプレート記述専用のプレースホルダ)。
data class ReviewCommandSpec(val name: String, val template: String)
