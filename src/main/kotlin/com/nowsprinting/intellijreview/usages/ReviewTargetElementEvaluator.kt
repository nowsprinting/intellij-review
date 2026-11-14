package com.nowsprinting.intellijreview.usages

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin

// 見出しのgetTextOffset()はリネームのためラベル葉の位置を返すので、既定のキャレット解決
// (「親のgetTextOffset()==葉の開始位置」判定)では見出しマーカー("="等)上から宣言に
// 到達できない。getTextOffset()を要素先頭に戻すとラベル上からのShift+F6が壊れるため、
// マーカー葉とキャプション葉をここで宣言へ対応付ける。キャプション中のインライン命令は
// 自身の参照(@<chap>等)を持つため対象外。
class ReviewTargetElementEvaluator : TargetElementEvaluatorEx2() {
    override fun getNamedElement(element: PsiElement): PsiElement? {
        if (element.elementType != ReviewTypes.HEADING_MARKER && element.elementType != ReviewTypes.TEXT) return null
        val heading = element.parent as? ReviewHeadingMixin ?: return null
        // レベル1見出しは@<hd>系で解決できず(ReviewDeclarationCacheのコメント参照)、
        // @<chap>/@<chapref>/@<title>が参照するのは章ファイルそのもの。
        if (heading.getLevel() == 1 && !heading.isColumn()) return heading.containingFile
        return heading
    }
}
