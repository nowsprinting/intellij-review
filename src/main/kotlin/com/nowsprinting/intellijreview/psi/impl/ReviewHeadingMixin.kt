package com.nowsprinting.intellijreview.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.util.IncorrectOperationException
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.psi.ReviewElementFactory
import com.nowsprinting.intellijreview.psi.ReviewTypes

abstract class ReviewHeadingMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    fun getLevel(): Int = node.findChildByType(ReviewTypes.HEADING_MARKER)?.textLength ?: 0

    fun isColumn(): Boolean =
        node.findChildByType(ReviewTypes.HEADING_OPTION)?.text == "column"

    // Structure View向け: "===[/column]"はcolumnの明示的な終了マーカーであり、
    // それ自体はコンテンツを持たないただの制御見出し(deepwikiでcompile_headlineの
    // タグが"/"始まりならcloseとして扱われることを確認済み)。
    fun isColumnClose(): Boolean =
        node.findChildByType(ReviewTypes.HEADING_OPTION)?.text == "/column"

    fun getLabel(): String? = node.findChildByType(ReviewTypes.HEADING_LABEL_ID)?.text

    // 参照解決(ReviewHeadingReference)はネストパス付きitem_id(ReviewDeclarationCache側で
    // 算出)を使うが、リネームの対象は常に明示ラベル自身の葉トークンであり別物。
    override fun getNameIdentifier(): PsiElement? = node.findChildByType(ReviewTypes.HEADING_LABEL_ID)?.psi

    // PsiElement.getTextOffset()の既定実装(要素全体の開始位置)のままだと、
    // TargetElementUtilの宣言側キャレット解決(getNamedElement)が
    // 「親の getTextOffset() == キャレット位置の葉の開始位置」という一致判定に失敗し、
    // Shift+F6が宣言位置から効かなくなる。ラベル葉の開始位置を返すよう上書きする。
    override fun getTextOffset(): Int = nameIdentifier?.textRange?.startOffset ?: super.getTextOffset()

    // @<hd>系はラベルが無ければ正規化キャプションで照合する(ReviewDeclarationCache参照)ため、
    // Find Usagesの検索語・リネーム対象もそれに揃える。
    override fun getName(): String = getLabel() ?: ReviewDeclarationCache.normalizeCaption(this)

    // インライン命令を含むキャプションは正規化後の名前(内容のみ)と見出しの字面が一致せず、
    // 書き換え方が一意に決まらないためリネーム対象外とする(ReviewVetoRenameCondition)。
    fun hasPlainCaption(): Boolean =
        node.findChildByType(ReviewTypes.TEXT) != null &&
            node.getChildren(null).none { it.psi is ReviewInlineCommandMixin }

    // ラベルはElementManipulator経由ではなく葉トークンを直接差し替える(専用マニピュレータは持たない)。
    // キャプションは複数のTEXT葉に分かれ得る("@"単独など)ため、見出し行ごと作り直す。
    override fun setName(name: String): PsiElement {
        nameIdentifier?.let {
            it.replace(ReviewElementFactory.createHeadingLabelId(project, name))
            return this
        }
        if (!hasPlainCaption()) throw IncorrectOperationException("インライン命令を含むキャプションの見出しはリネームできません")
        val firstText = node.findChildByType(ReviewTypes.TEXT)!!
        val prefix = text.substring(0, firstText.startOffsetInParent) + firstText.text.takeWhile { it == ' ' || it == '\t' }
        return replace(ReviewElementFactory.createHeading(project, "$prefix$name\n"))
    }
}
