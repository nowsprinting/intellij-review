package com.nowsprinting.intellijreview.structure

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

data class ReviewHeadingNode(val heading: ReviewHeadingMixin, val children: MutableList<ReviewHeadingNode> = mutableListOf())

object ReviewHeadingTreeBuilder {
    // Re:VIEW本体のCompiler#compile_headline(deepwikiで実測確認)を参考にした設計:
    // 通常見出しのネスト(祖先=直前の、より浅いレベルの見出し)と、[column]見出しの
    // 暗黙終了(同位以上の見出しが来ると閉じる)は、どちらも「新しい見出しのlevel以上の
    // スタック上位を閉じる(pop)」という同一ルールで表現できる。columnは他の見出しと
    // 同じスタックに積まれ、popされるまでの間にネストした見出しをそのまま子として持つ
    // (Re:VIEW本体でもcolumn内は構造的にネストしたブロックとして扱われる)。
    fun build(psiFile: PsiFile): List<ReviewHeadingNode> {
        val roots = mutableListOf<ReviewHeadingNode>()
        val stack = mutableListOf<Triple<Int, ReviewHeadingNode, Boolean>>() // level, node, isColumn

        val headings = PsiTreeUtil.findChildrenOfType(psiFile, ReviewHeadingMixin::class.java)
            .sortedBy { it.textOffset }

        for (heading in headings) {
            if (heading.isColumnClose()) {
                // "[/column]"はcolumnの明示的な終了マーカーであり、それ自体はツリーの
                // ノードを持たない(Re:VIEW本体でもコンテンツを持たない制御見出し)。
                // column内にネストした通常の見出しがスタック最上位に乗っている場合があるため、
                // 最上位だけでなく直近(最も内側)のcolumnをスタックから探して、それより上を
                // まとめて閉じる(Re:VIEW本体のclose_tagged_sectionは@tagged_sectionという
                // column専用スタックを別途持つが、本プラグインは見出しネスト計算と統一した
                // 単一スタックで表現しているため、この探索が必要になる)。
                val columnIndex = stack.indexOfLast { it.third }
                if (columnIndex >= 0) {
                    while (stack.size > columnIndex) stack.removeAt(stack.size - 1)
                }
                continue
            }

            val level = heading.getLevel()
            while (stack.isNotEmpty() && stack.last().first >= level) stack.removeAt(stack.size - 1)

            val node = ReviewHeadingNode(heading)
            if (stack.isEmpty()) roots += node else stack.last().second.children += node
            stack += Triple(level, node, heading.isColumn())
        }
        return roots
    }
}
