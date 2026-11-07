package com.nowsprinting.intellijreview.declaration

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.nowsprinting.intellijreview.lang.ReviewFileType
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

// プロジェクト内の全.reファイルを走査して収集した宣言(章・ブロックID・見出しラベル)の
// 一覧。Step 5の補完に加え、Step 6-7の参照解決でも同じキャッシュを再利用する。
object ReviewDeclarationCache {

    // //imgtableは@<img>と@<table>の両方から参照されうるため複数kindを持つ。
    private val BLOCK_KINDS_BY_COMMAND: Map<String, List<ReviewDeclarationKind>> = mapOf(
        "list" to listOf(ReviewDeclarationKind.LIST),
        "listnum" to listOf(ReviewDeclarationKind.LIST),
        "image" to listOf(ReviewDeclarationKind.IMAGE),
        "indepimage" to listOf(ReviewDeclarationKind.IMAGE),
        "numberlessimage" to listOf(ReviewDeclarationKind.IMAGE),
        "graph" to listOf(ReviewDeclarationKind.IMAGE),
        "imgtable" to listOf(ReviewDeclarationKind.IMAGE, ReviewDeclarationKind.TABLE),
        "table" to listOf(ReviewDeclarationKind.TABLE),
        "texequation" to listOf(ReviewDeclarationKind.EQUATION),
        "footnote" to listOf(ReviewDeclarationKind.FOOTNOTE),
        "endnote" to listOf(ReviewDeclarationKind.ENDNOTE),
        "bibpaper" to listOf(ReviewDeclarationKind.BIBPAPER),
        "label" to listOf(ReviewDeclarationKind.LABEL),
    )

    fun getDeclarations(project: Project): List<ReviewDeclaration> =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(computeDeclarations(project), PsiModificationTracker.MODIFICATION_COUNT)
        }

    fun byKind(project: Project, kind: ReviewDeclarationKind): List<ReviewDeclaration> =
        getDeclarations(project).filter { it.kind == kind }

    private fun computeDeclarations(project: Project): List<ReviewDeclaration> {
        val declarations = mutableListOf<ReviewDeclaration>()
        declarations += chapterDeclarations(project)

        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        for (virtualFile in FileTypeIndex.getFiles(ReviewFileType, scope)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue

            for (block in PsiTreeUtil.findChildrenOfType(psiFile, ReviewBlockCommandMixin::class.java)) {
                val id = block.getIdArg() ?: continue
                for (kind in BLOCK_KINDS_BY_COMMAND[block.getCommandName()].orEmpty()) {
                    declarations += ReviewDeclaration(id, kind, psiFile, block)
                }
            }

            declarations += headingDeclarations(psiFile)
        }
        return declarations
    }

    // Re:VIEW本体のIndexBuilder#headlineを参考にした設計: column付き見出しは別index
    // (ColumnIndex)扱いで、通常見出しのネストパス計算(@headline_stack)には一切参加しない。
    // レベル1見出し(章タイトル相当)はheadline_stackの対象外(@<chap>/@<title>でのみ到達可能)
    // であり、@<hd>系では解決できないためHEADING宣言を作らない(deepwikiで実測確認)。
    private fun headingDeclarations(psiFile: PsiFile): List<ReviewDeclaration> {
        val declarations = mutableListOf<ReviewDeclaration>()
        // stack[level - 2]に各階層の識別子(明示ラベル優先、なければ正規化キャプション)を積む。
        val stack = mutableListOf<String>()
        val headings = PsiTreeUtil.findChildrenOfType(psiFile, ReviewHeadingMixin::class.java)
            .sortedBy { it.textOffset }

        for (heading in headings) {
            val ownId = heading.getLabel() ?: normalizeCaption(heading)
            if (heading.isColumn()) {
                declarations += ReviewDeclaration(ownId, ReviewDeclarationKind.COLUMN, psiFile, heading)
                continue
            }
            val level = heading.getLevel()
            if (level < 2) continue

            while (stack.size > level - 2) stack.removeAt(stack.size - 1)
            stack.add(ownId)
            declarations += ReviewDeclaration(stack.joinToString("|"), ReviewDeclarationKind.HEADING, psiFile, heading)
        }
        return declarations
    }

    // heading_caption(private規則、TEXT/inline_commandの並び)をプレーンテキスト化する。
    // インライン命令はgetContentText()(アンエスケープ済み)の内容のみを採用する。
    // Structure View(見出しの表示テキストは常にキャプション)からも再利用するため公開する。
    fun normalizeCaption(heading: ReviewHeadingMixin): String {
        val sb = StringBuilder()
        for (child in heading.node.getChildren(null)) {
            when {
                child.elementType == ReviewTypes.TEXT -> sb.append(child.text)
                child.psi is ReviewInlineCommandMixin -> sb.append((child.psi as ReviewInlineCommandMixin).getContentText())
            }
        }
        return sb.toString().trim()
    }

    private fun chapterDeclarations(project: Project): List<ReviewDeclaration> {
        val scope = GlobalSearchScope.projectScope(project)
        val catalogFile = FilenameIndex.getVirtualFilesByName(ReviewBookLayout.CATALOG_YAML, scope).firstOrNull() ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        val catalogPsiFile = psiManager.findFile(catalogFile) ?: return emptyList()
        val chapterDir = ReviewBookLayout.chapterDir(project, catalogFile) ?: return emptyList()

        return ReviewCatalogYamlParser.parseChapterIds(catalogPsiFile.text).mapNotNull { id ->
            val chapterFile = chapterDir.findChild("$id.re") ?: return@mapNotNull null
            val chapterPsiFile = psiManager.findFile(chapterFile) ?: return@mapNotNull null
            ReviewDeclaration(id, ReviewDeclarationKind.CHAPTER, chapterPsiFile, chapterPsiFile)
        }
    }
}
