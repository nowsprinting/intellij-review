package com.nowsprinting.intellijreview.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.nowsprinting.intellijreview.ReviewBundle
import com.nowsprinting.intellijreview.completion.ReviewCommandCatalog
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationCache
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind
import com.nowsprinting.intellijreview.psi.ReviewFile
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

// "<>"や"{}"の対応崩れはパーサーのエラー要素として既に赤表示されるため、ここでは扱わない。
// 例外は閉じ"//}"の欠落で、パーサーのエラーはファイル末尾に出て開始位置から遠いため命令名にも出す。
class ReviewAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when {
            element is ReviewInlineCommandMixin -> annotateInlineCommand(element, holder)
            element is ReviewBlockCommandMixin -> annotateBlockCommand(element, holder)
            element is ReviewFile -> annotateFile(element, holder)
            element.elementType in URL_SCANNED_TOKENS -> annotateBareUrl(element, holder)
        }
    }

    private fun annotateInlineCommand(command: ReviewInlineCommandMixin, holder: AnnotationHolder) {
        val name = command.getCommandName()
        // 名前が空("@<>")の場合はパーサーのエラーと重複するため出さない。
        if (name.isNotEmpty() && name !in ReviewCommandCatalog.inlineCommands(command.containingFile)) {
            val nameLeaf = command.node.findChildByType(ReviewTypes.INLINE_NAME)!!
            holder.newAnnotation(HighlightSeverity.ERROR, ReviewBundle.message("annotator.unknownInlineCommand", name))
                .range(nameLeaf.textRange).create()
            return
        }

        // 章跨ぎ参照("chap|id")は章の参照が先に並ぶため、章が無ければ章だけを報告する
        // (章が解決できなければIDも必ず解決できず、重ねて出しても情報が増えない)。
        val unresolved = command.references.firstOrNull { it.resolve() == null } ?: return
        holder.newAnnotation(HighlightSeverity.ERROR, ReviewBundle.message("annotator.unresolvedReference", unresolved.canonicalText))
            .range(unresolved.rangeInElement.shiftRight(command.textRange.startOffset)).create()
    }

    private fun annotateBlockCommand(block: ReviewBlockCommandMixin, holder: AnnotationHolder) {
        val nameLeaf = block.node.findChildByType(ReviewTypes.BLOCK_COMMAND_NAME)!!
        val name = block.getCommandName()
        if (name !in ReviewCommandCatalog.blockCommands(block.containingFile).map { it.name }) {
            holder.newAnnotation(HighlightSeverity.ERROR, ReviewBundle.message("annotator.unknownBlockCommand", name))
                .range(nameLeaf.textRange).create()
        }

        val body = block.node.findChildByType(ReviewTypes.BLOCK_BODY) ?: return
        if (body.findChildByType(ReviewTypes.BLOCK_END) != null) return
        holder.newAnnotation(HighlightSeverity.ERROR, ReviewBundle.message("annotator.unclosedBlock"))
            .range(nameLeaf.textRange).create()
    }

    // 脚注の参照数やIDの重複はファイル全体の集計が要るため、要素ごとでなくファイル単位で1回だけ数える。
    private fun annotateFile(file: PsiFile, holder: AnnotationHolder) {
        val fnUsages = PsiTreeUtil.findChildrenOfType(file, ReviewInlineCommandMixin::class.java)
            .filter { it.getCommandName() == "fn" }
            .groupBy { it.getContentText() }
        val declarations = ReviewDeclarationCache.getDeclarations(file.project).filter { it.file == file }

        for (footnote in declarations.filter { it.kind == ReviewDeclarationKind.FOOTNOTE && it.id.isNotEmpty() }) {
            if (fnUsages[footnote.id].isNullOrEmpty()) {
                holder.newAnnotation(HighlightSeverity.WARNING, ReviewBundle.message("annotator.unreferencedFootnote", footnote.id))
                    .range(idRange(footnote.element)).create()
            }
        }

        // HTMLBuilderは@<fn>ごとにid="fnb-<id>"を出力するため、同じ脚注を2回参照するとEPUB内でidが重複する。
        for ((id, usages) in fnUsages) {
            if (usages.size < 2) continue
            for (usage in usages) {
                holder.newAnnotation(HighlightSeverity.WARNING, ReviewBundle.message("annotator.multipleFootnoteReferences", id))
                    .range(idRange(usage)).create()
            }
        }

        // HTMLBuilderはlist/image/tableのいずれもIDをそのままdivのidに使うため、種類をまたいだ重複でもidが衝突する。
        // //imgtableはIMAGEとTABLEの両方に登録されるので、要素単位で数えて自身との重複を除く。
        declarations.filter { it.kind in HTML_ID_KINDS && it.id.isNotEmpty() }
            .groupBy({ it.id }, { it.element })
            .mapValues { it.value.distinct() }
            .filterValues { it.size > 1 }
            .forEach { (id, elements) ->
                for (element in elements) {
                    holder.newAnnotation(HighlightSeverity.WARNING, ReviewBundle.message("annotator.duplicateBlockId", id))
                        .range(idRange(element)).create()
                }
            }
    }

    private fun annotateBareUrl(leaf: PsiElement, holder: AnnotationHolder) {
        val block = PsiTreeUtil.getParentOfType(leaf, ReviewBlockCommandMixin::class.java)
        if (block != null && block.getCommandName() in VERBATIM_BLOCK_COMMANDS) return
        for (match in URL.findAll(leaf.text)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ReviewBundle.message("annotator.bareUrl"))
                .range(TextRange(match.range.first, match.range.last + 1).shiftRight(leaf.textRange.startOffset)).create()
        }
    }

    private fun idRange(element: PsiElement): TextRange = when (element) {
        is ReviewBlockCommandMixin -> element.nameIdentifier?.textRange
        else -> element.references.firstOrNull()?.rangeInElement?.shiftRight(element.textRange.startOffset)
    } ?: element.textRange

    companion object {
        private val URL_SCANNED_TOKENS = setOf(ReviewTypes.TEXT, ReviewTypes.BLOCK_ARG_TEXT, ReviewTypes.RAW_TEXT)

        // コード例など、URLをリンクにしないことが意図どおりである本文を持つブロック。
        private val VERBATIM_BLOCK_COMMANDS = setOf(
            "list", "listnum", "emlist", "emlistnum", "cmd", "source", "box",
            "raw", "rawblock", "embed", "texequation", "graph", "comment",
        )

        private val HTML_ID_KINDS = setOf(ReviewDeclarationKind.LIST, ReviewDeclarationKind.IMAGE, ReviewDeclarationKind.TABLE)

        // 和文に直接続くURL("https://example.com/を参照")で和文まで巻き込まないよう、URLに使えるASCII文字に限る。
        private val URL = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")
    }
}
