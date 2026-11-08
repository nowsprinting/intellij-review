package com.nowsprinting.intellijreview.preview

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.nowsprinting.intellijreview.declaration.ReviewBookLayout
import com.nowsprinting.intellijreview.declaration.ReviewCatalogYamlParser
import com.nowsprinting.intellijreview.declaration.ReviewConfigYamlParser
import com.intellij.openapi.vfs.VirtualFile
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.BOLD_END
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.BOLD_START
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Element
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Figure
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Footnote
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Gap
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.NOTE_END
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.NOTE_START
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Padding
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.PageBreak
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Raw
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Space
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Style
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.TT_END
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.TT_START
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Text
import com.nowsprinting.intellijreview.psi.ReviewBlockBody
import com.nowsprinting.intellijreview.psi.ReviewTypes
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.min

// .reファイル1つを、PDF(LaTeX)出力の見当をつけるための参考レンダリングにする。
// Re:VIEW本体を実行しないため、見出し・図・コードなどの高さはjsbook.clsの寸法から近似した見積もり。
// 参照系インライン命令の書式はRe:VIEWのi18n.yml(ja)に合わせている。
class ReviewPreviewRenderer(private val file: PsiFile, private val images: ReviewPreviewImageCache = ReviewPreviewImageCache()) {
    private val project = file.project
    private val config = file.virtualFile?.let { ReviewBookLayout.baseDir(project, it) }
        ?.let { ReviewBookLayout.configText(project, it) }
    val settings = ReviewPageSettings.from(config?.let { ReviewConfigYamlParser.parseList(it, "texdocumentclass") })
    private val secnolevel = config?.let { ReviewConfigYamlParser.parseValue(it, "secnolevel")?.toIntOrNull() } ?: 2

    // 章ID → 図表番号などの接頭辞("1"、付録は"A")。PREDEF・POSTDEFの章は番号を持たない。
    private val chapterPrefixes: Map<String, String?> = run {
        val catalog = ReviewBookLayout.catalogFile(project)?.let { PsiManager.getInstance(project).findFile(it)?.text }
            ?: return@run emptyMap()
        var chapterCount = 0
        var appendixCount = 0
        ReviewCatalogYamlParser.parseChapters(catalog).associate { (id, section) ->
            id to when (section) {
                "CHAPS" -> (++chapterCount).toString()
                "APPENDIX" -> ('A' + appendixCount++).toString()
                else -> null
            }
        }
    }

    private val headingNumberCache = mutableMapOf<PsiFile, Map<ReviewHeadingMixin, String>>()

    fun paginate(): List<List<ReviewPageLayout.Placed>> = ReviewPageLayout.paginate(elements(), settings)

    fun elements(): List<Element> = Builder().build()

    private inner class Builder {
        val elements = mutableListOf<Element>()
        val pending = StringBuilder()
        var pendingFirstIndent = 0
        var pendingHangIndent = 0
        var pendingOffset = -1
        var pendingIsListItem = false
        var afterDefinitionTerm = false
        var noindent = false
        var inList = false

        fun build(): List<Element> {
            var afterComment = false
            for (child in file.node.getChildren(null)) {
                val psi = child.psi
                when {
                    psi is PsiComment -> { afterComment = true; continue }
                    child.elementType == ReviewTypes.EOL -> if (!afterComment) { endList(); afterDefinitionTerm = false }
                    child.elementType == ReviewTypes.PARAGRAPH -> paragraph(child)
                    child.elementType == ReviewTypes.LIST_ITEM -> listItem(child)
                    psi is ReviewHeadingMixin -> { endList(); heading(psi) }
                    psi is ReviewBlockCommandMixin -> { endList(); block(psi) }
                }
                afterComment = false
            }
            endList()
            return elements
        }

        fun flush() {
            if (pending.isNotEmpty()) elements += Text(pending.toString(), pendingFirstIndent, pendingHangIndent, offset = pendingOffset)
            pending.setLength(0)
            pendingIsListItem = false
        }

        fun endList() {
            flush()
            if (inList) elements += Gap(0.5)
            inList = false
        }

        fun start(text: String, firstIndent: Int, hangIndent: Int, isListItem: Boolean, offset: Int) {
            pending.append(text)
            pendingFirstIndent = firstIndent
            pendingHangIndent = hangIndent
            pendingIsListItem = isListItem
            pendingOffset = offset
        }

        // Re:VIEWは空行までの連続した行を1段落に連結する。和文同士は改行を詰め、欧文同士は空白1つにする
        // (TeXが和文文字の前後の改行を無視するのと同じ結果)。
        fun append(text: String) {
            val last = pending.lastOrNull { !ReviewPageLayout.isMarker(it) }
            if (last in '!'..'~' && text.firstOrNull { !ReviewPageLayout.isMarker(it) } in '!'..'~') pending.append(' ')
            pending.append(text)
        }

        fun paragraph(node: ASTNode) {
            val text = lineText(node)
            val indented = node.text.firstOrNull()?.isWhitespace() == true
            when {
                indented && (pendingIsListItem || afterDefinitionTerm) && pending.isNotEmpty() -> append(text.trim())
                indented && afterDefinitionTerm -> start(text.trim(), 2, 2, true, node.startOffset)
                pending.isNotEmpty() && !pendingIsListItem -> append(text.trim())
                else -> {
                    endList()
                    start(text.trim(), if (noindent) 0 else 1, 0, false, node.startOffset)
                    noindent = false
                }
            }
        }

        // jsbook.clsの\@listi: 箇条書きの前後に\topsep(0.5行)のアキ。入れ子の箇条書きは\topsepが0
        fun listItem(node: ASTNode) {
            flush()
            if (!inList) elements += Gap(0.5)
            inList = true
            afterDefinitionTerm = false
            val marker = node.firstChildNode
            val text = lineText(node).trim()
            when (marker.elementType) {
                ReviewTypes.UL_MARKER -> {
                    val depth = marker.text.count { it == '*' }
                    start("・$text", depth - 1, depth, true, node.startOffset)
                }
                ReviewTypes.OL_MARKER -> start("${marker.text.trim()} $text", 1, 2, true, node.startOffset)
                else -> {
                    elements += Text(text, offset = node.startOffset)
                    afterDefinitionTerm = true
                }
            }
        }

        fun heading(heading: ReviewHeadingMixin) {
            val option = heading.node.findChildByType(ReviewTypes.HEADING_OPTION)?.text
            if (heading.isColumnClose() || option == "nodisp") return
            val caption = lineText(heading.node).trim()
            val level = heading.getLevel()
            val offset = heading.node.startOffset
            when {
                heading.isColumn() -> {
                    elements += Gap()
                    elements += Text("■コラム　$caption", style = Style.HEADING, offset = offset)
                    elements += Gap(0.5)
                }
                // jsbook.clsの\@makechapterhead: 前アキ2行、章番号(\huge)、1行アキ、章タイトル(\Huge)、後アキ3行
                level == 1 -> {
                    elements += PageBreak
                    elements += Space(2.0)
                    chapterLabel(file)?.let { elements += Raw(it, Style.CHAPTER_NUMBER, offset); elements += Space(1.0) }
                    elements += Text(caption, style = Style.CHAPTER_TITLE, offset = offset)
                    elements += Space(3.0)
                }
                // jsbook.clsの\section・\subsection: 前アキ1行・後アキ0.5行、\subsubsection以下: 前アキ1行・後アキなし
                else -> {
                    val number = headingNumbers(file)[heading]
                    val style = when (level) {
                        2 -> Style.SECTION
                        3 -> Style.SUBSECTION
                        else -> Style.HEADING
                    }
                    elements += Gap()
                    elements += Text(if (number != null) "$number　$caption" else caption, style = style, offset = offset)
                    if (level <= 3) elements += Gap(0.5)
                }
            }
        }

        fun block(block: ReviewBlockCommandMixin) {
            val name = block.getCommandName()
            val args = blockArgs(block)
            // ponytail: ブロック内の各行はブロックの先頭位置で代表させる。長いコードの途中にスクロールを合わせるなら行ごとの位置を持たせる
            val offset = block.node.startOffset
            when (name) {
                in CODE_BLOCKS -> {
                    val caption = when (name) {
                        "list", "listnum" -> "${itemLabel(block)}: ${args.getOrElse(1) { "" }}"
                        "texequation" -> args.getOrNull(1)
                        else -> args.lastOrNull()
                    }
                    elements += Gap(0.5)
                    if (!caption.isNullOrBlank()) elements += Text(caption, style = Style.CAPTION, offset = offset)
                    val numbered = name.endsWith("num")
                    val padding = Padding(FRAME_SEP_PT / settings.baselineskipPt)
                    elements += padding
                    bodyLines(block).forEachIndexed { i, line ->
                        elements += Raw(if (numbered) "${(i + 1).toString().padStart(2)}: $line" else line, Style.CODE, offset)
                    }
                    elements += padding
                    elements += Gap(0.5)
                }
                in IMAGE_BLOCKS -> {
                    // //imageの引数は[ID][キャプション][メトリック]、//graphは[ID][ツール名][キャプション]
                    val caption = args.getOrElse(if (name == "graph") 2 else 1) { "" }
                    val label = if (name in NUMBERED_GROUP) "${itemLabel(block)}: $caption" else caption
                    elements += Gap(0.5)
                    if (name == "imgtable") elements += Text(label, style = Style.FLOAT_CAPTION, offset = offset)
                    elements += figure(args.getOrElse(0) { "" }, if (name == "graph") null else args.getOrNull(2), offset)
                    if (name != "imgtable" && label.isNotBlank()) elements += Text(label, style = Style.FLOAT_CAPTION, offset = offset)
                    elements += Gap(0.5)
                }
                "table" -> {
                    elements += Gap(0.5)
                    elements += Text("${itemLabel(block)}: ${args.getOrElse(1) { "" }}", style = Style.FLOAT_CAPTION, offset = offset)
                    bodyLines(block).filterNot { TABLE_SEPARATOR.matches(it) }
                        .forEach { elements += Raw(it.split(Regex("\t+")).joinToString(" │ "), offset = offset) }
                    elements += Gap(0.5)
                }
                "noindent" -> noindent = true
                "blankline" -> elements += Space(1.0)
                "pagebreak", "clearpage" -> elements += PageBreak
                "footnote" -> elements += Footnote(footnoteMark(block), args.getOrElse(1) { "" })
                in SKIPPED_BLOCKS -> Unit
                else -> {
                    // note・memo・quoteなどの囲み: 本文は段落として組み、全角1字ぶん内側に寄せる
                    elements += Gap(0.5)
                    args.lastOrNull()?.takeIf { it.isNotBlank() }?.let { elements += Text("■$it", 1, 1, Style.HEADING, offset) }
                    for (paragraph in paragraphs(bodyLines(block))) elements += Text(paragraph, 2, 1, offset = offset)
                    elements += Gap(0.5)
                }
            }
        }
    }

    // ---- 図 ----

    // Re:VIEWのimage_scale2width(既定で有効)により、scale=は画像の実寸でなく版面の幅に対する比として扱われる。
    // scale指定が無ければ、版面の幅を上限に画像の実寸で置かれる(\maxwidth)。
    private fun figure(id: String, metric: String?, offset: Int): Figure {
        // LaTeX向けにPDF・EPSを置き、EPUB向けに同名のPNGを並べる構成が多い。ImageIOはPDF・EPSを読めないため、
        // Re:VIEWが選ぶファイルが読めなければ同名の読める画像で代用する(縦横比は同じとみなす)。
        val image = findImage(id)?.let { found ->
            images.load(found) ?: READABLE_EXTENSIONS.firstNotNullOfOrNull { found.parent?.findChild("${found.nameWithoutExtension}.$it") }
                ?.let { images.load(it) }
        }
        val scale = metric?.let { SCALE_METRIC.find(it) }?.groupValues?.get(1)?.toDoubleOrNull()
        val ratio = min(1.0, scale ?: image?.let { it.naturalWidth * PT_PER_PX / settings.textWidthPt } ?: 1.0)
        val height = image?.let { ratio * settings.textWidthPt * it.naturalHeight / it.naturalWidth / settings.baselineskipPt }
            ?: PLACEHOLDER_IMAGE_LINES.toDouble()
        return Figure(id, image?.image, ratio, min(height, settings.linesPerPage - 2.0), offset)
    }

    // Re:VIEW本体(Book::ImageFinder)と同じ探索順。ビルダー名はPDF出力のlatex。
    private fun findImage(id: String): VirtualFile? {
        val reFile = file.virtualFile ?: return null
        val imageDir = ReviewBookLayout.imageDir(project, reFile) ?: return null
        val chapterId = reFile.nameWithoutExtension
        return listOf("latex/$chapterId/$id", "latex/$chapterId-$id", "latex/$id", "$chapterId/$id", "$chapterId-$id", id)
            .firstNotNullOfOrNull { path -> IMAGE_EXTENSIONS.firstNotNullOfOrNull { imageDir.findFileByRelativePath("$path.$it") } }
    }

    // ---- 文字列化 ----

    private fun lineText(node: ASTNode): String {
        val sb = StringBuilder()
        for (child in node.getChildren(null)) {
            val psi = child.psi
            when {
                psi is ReviewInlineCommandMixin -> sb.append(inlineText(psi))
                child.elementType == ReviewTypes.TEXT || child.elementType == ReviewTypes.RAW_TEXT -> sb.append(child.text)
            }
        }
        return sb.toString()
    }

    private fun blockArgs(block: ReviewBlockCommandMixin): List<String> {
        val args = mutableListOf<String>()
        var current: StringBuilder? = null
        for (child in block.node.getChildren(null)) {
            val psi = child.psi
            when {
                child.elementType == ReviewTypes.LBRACKET -> current = StringBuilder()
                child.elementType == ReviewTypes.RBRACKET -> { current?.let { args += it.toString() }; current = null }
                child.elementType == ReviewTypes.BLOCK_ARG_TEXT -> current?.append(child.text.replace("\\]", "]"))
                psi is ReviewInlineCommandMixin -> current?.append(inlineText(psi))
            }
        }
        return args
    }

    private fun bodyLines(block: ReviewBlockCommandMixin): List<String> {
        val body = PsiTreeUtil.getChildOfType(block, ReviewBlockBody::class.java) ?: return emptyList()
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var started = false
        for (child in body.node.getChildren(null)) {
            val psi = child.psi
            when {
                child.elementType == ReviewTypes.EOL -> { if (started) lines += sb.toString(); sb.setLength(0); started = true }
                child.elementType == ReviewTypes.RAW_TEXT -> sb.append(child.text)
                psi is ReviewInlineCommandMixin -> sb.append(inlineText(psi))
            }
        }
        return lines
    }

    private fun paragraphs(lines: List<String>): List<String> =
        lines.joinToString("\n").split(Regex("\n[ \t]*\n")).map { it.lines().joinToString("") { line -> line.trim() } }
            .filter { it.isNotBlank() }

    // ---- インライン命令 ----

    private fun inlineText(command: ReviewInlineCommandMixin): String {
        val content = command.getContentText()
        return when (val name = command.getCommandName()) {
            "chap", "title", "chapref" -> {
                val target = command.references.firstOrNull()?.resolve() as? PsiFile
                val label = target?.let { chapterLabel(it) }
                val title = target?.let { chapterTitle(it) } ?: content
                when (name) {
                    "chap" -> label ?: title
                    "title" -> title
                    else -> if (label != null) "$label「$title」" else "「$title」"
                }
            }
            "list", "img", "table" -> {
                val target = command.references.lastOrNull()?.resolve() as? ReviewBlockCommandMixin
                target?.let { itemLabel(it) } ?: "${KIND_LABELS[name]}??"
            }
            "hd", "sec", "secref", "sectitle" -> {
                val target = command.references.lastOrNull()?.resolve() as? ReviewHeadingMixin
                    ?: return "「$content」"
                val caption = lineText(target.node).trim()
                val number = headingNumbers(target.containingFile)[target]
                when (name) {
                    "sec" -> number ?: ""
                    "sectitle" -> caption
                    else -> if (number != null) "「$number $caption」" else "「$caption」"
                }
            }
            "fn" -> {
                val target = command.references.lastOrNull()?.resolve() as? ReviewBlockCommandMixin
                "$NOTE_START${target?.let { footnoteMark(it) } ?: "*?"}$NOTE_END"
            }
            "br" -> "\n"
            "ruby" -> content.substringBefore(',')
            // review-base.styの\reviewkwは太字(\textbf\textgt)。読みは太字にしない
            "kw" -> if (content.contains(',')) "$BOLD_START${content.substringBefore(',')}$BOLD_END（${content.substringAfter(',').trim()}）"
            else "$BOLD_START$content$BOLD_END"
            "b", "strong" -> "$BOLD_START$content$BOLD_END"
            "tt", "code", "ttb", "tti" -> "$TT_START$content$TT_END"
            "href" -> if (content.contains(',')) content.substringAfter(',').trim() else content
            "uchar" -> content.toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: ""
            "comment", "hidx", "raw", "embed" -> ""
            else -> content
        }
    }

    // ---- 番号付け ----

    private fun chapterId(file: PsiFile): String? = file.virtualFile?.nameWithoutExtension

    private fun chapterPrefix(file: PsiFile): String? = chapterId(file)?.let { chapterPrefixes[it] }

    private fun chapterLabel(file: PsiFile): String? = chapterPrefix(file)?.let {
        if (it.first().isDigit()) "第${it}章" else "付録$it"
    }

    private fun chapterTitle(file: PsiFile): String? =
        PsiTreeUtil.findChildrenOfType(file, ReviewHeadingMixin::class.java).firstOrNull { it.getLevel() == 1 }
            ?.let { lineText(it.node).trim() }

    // 見出し番号("1.2"など)。番号が付くのはsecnolevelまでの階層で、[nonum]・[notoc]・[nodisp]付きとコラムは数えない。
    private fun headingNumbers(file: PsiFile): Map<ReviewHeadingMixin, String> = headingNumberCache.getOrPut(file) {
        val prefix = chapterPrefix(file) ?: return@getOrPut emptyMap()
        val counters = IntArray(MAX_LEVEL + 1)
        val numbers = mutableMapOf<ReviewHeadingMixin, String>()
        for (heading in PsiTreeUtil.findChildrenOfType(file, ReviewHeadingMixin::class.java).sortedBy { it.textOffset }) {
            val level = heading.getLevel()
            val option = heading.node.findChildByType(ReviewTypes.HEADING_OPTION)?.text
            if (level !in 2..MAX_LEVEL || heading.isColumn() || heading.isColumnClose() || option in UNNUMBERED_OPTIONS) continue
            counters[level]++
            for (deeper in level + 1..MAX_LEVEL) counters[deeper] = 0
            if (level <= secnolevel) numbers[heading] = (listOf(prefix) + (2..level).map { counters[it] }).joinToString(".")
        }
        numbers
    }

    // "リスト1.2"のような図表番号。番号は同じ章ファイル内で同じ種類のブロックを先頭から数えた順番。
    private fun itemLabel(block: ReviewBlockCommandMixin): String {
        val group = NUMBERED_GROUP[block.getCommandName()] ?: return ""
        val n = PsiTreeUtil.findChildrenOfType(block.containingFile, ReviewBlockCommandMixin::class.java)
            .count { NUMBERED_GROUP[it.getCommandName()] == group && it.textOffset <= block.textOffset }
        val prefix = chapterPrefix(block.containingFile)
        return group + if (prefix != null) "$prefix.$n" else "$n"
    }

    // jsbook.clsの\thefootnoteは"*1"のような番号
    private fun footnoteMark(block: ReviewBlockCommandMixin): String =
        "*" + PsiTreeUtil.findChildrenOfType(block.containingFile, ReviewBlockCommandMixin::class.java)
            .count { it.getCommandName() == block.getCommandName() && it.textOffset <= block.textOffset }

    companion object {
        private const val MAX_LEVEL = 6

        // 画像が見つからない・読めない(PDF・EPSなど)ときに図が占める行数
        private const val PLACEHOLDER_IMAGE_LINES = 8

        // ponytail: 画像の解像度情報を読まず72dpiとみなして実寸を出している。dpi付きの画像で
        // ずれるなら、ImageIOのメタデータ(javax_imageio_1.0のHorizontalPixelSize)を読む
        private const val PT_PER_PX = 72.27 / 72

        // Re:VIEW本体のLATEXBuilder#image_extと同じ優先順
        private val IMAGE_EXTENSIONS = listOf("ai", "eps", "pdf", "tif", "tiff", "png", "bmp", "jpg", "jpeg", "gif")
        private val READABLE_EXTENSIONS = listOf("png", "jpg", "jpeg", "gif", "bmp")
        private val SCALE_METRIC = Regex("""(?:^|,)\s*(?:latex::)?scale=([0-9.]+)""")

        private val CODE_BLOCKS = setOf("list", "listnum", "emlist", "emlistnum", "source", "cmd", "box", "texequation")
        private val IMAGE_BLOCKS = setOf("image", "indepimage", "numberlessimage", "graph", "imgtable")

        // framed.styの\FrameSep(3\fboxsep)。shadedの網掛けは本文の上下と左右の版面外にこれだけ広がる
        const val FRAME_SEP_PT = 9.0

        private val SKIPPED_BLOCKS = setOf(
            "endnote", "bibpaper", "comment", "label", "tsize", "latextsize", "raw", "embed",
            "printendnotes", "beginchild", "endchild", "firstlinenum", "olnum",
        )
        private val UNNUMBERED_OPTIONS = setOf("nonum", "notoc", "nodisp")
        private val TABLE_SEPARATOR = Regex("""^[-=]{12,}$""")

        private val NUMBERED_GROUP = mapOf(
            "list" to "リスト", "listnum" to "リスト",
            "image" to "図", "graph" to "図",
            "table" to "表", "imgtable" to "表",
        )
        private val KIND_LABELS = mapOf("list" to "リスト", "img" to "図", "table" to "表")
    }
}

// 打鍵のたびに画像を読み直さないよう、ファイルの更新スタンプ単位で縮小済みの画像を保持する。
class ReviewPreviewImageCache {
    class Loaded(val image: BufferedImage?, val naturalWidth: Int, val naturalHeight: Int)

    private val cache = ConcurrentHashMap<Pair<String, Long>, Loaded>()

    fun load(file: VirtualFile): Loaded? =
        cache.computeIfAbsent(file.path to file.modificationStamp) { read(file) }.takeIf { it.naturalWidth > 0 }

    private fun read(file: VirtualFile): Loaded {
        val image = runCatching { file.inputStream.use { ImageIO.read(it) } }.getOrNull() ?: return Loaded(null, 0, 0)
        if (image.width <= MAX_WIDTH_PX) return Loaded(image, image.width, image.height)
        val height = image.height * MAX_WIDTH_PX / image.width
        val scaled = BufferedImage(MAX_WIDTH_PX, height, BufferedImage.TYPE_INT_ARGB)
        scaled.createGraphics().apply {
            drawImage(image.getScaledInstance(MAX_WIDTH_PX, height, Image.SCALE_SMOOTH), 0, 0, null)
            dispose()
        }
        return Loaded(scaled, image.width, image.height)
    }

    companion object {
        // 表示上はペインの幅程度あれば足り、大きな画像をそのまま持つとメモリを圧迫する
        private const val MAX_WIDTH_PX = 1200
    }
}
