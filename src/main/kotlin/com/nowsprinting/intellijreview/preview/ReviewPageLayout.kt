package com.nowsprinting.intellijreview.preview

import java.awt.image.BufferedImage
import kotlin.math.floor
import kotlin.math.max

// 版面(1行の字数・1ページの行数)に沿って、行分割と改ページを計算する。
// 幅は半角=1・全角=2の単位で数え(1行の字数は全角換算なので上限は字数×2)、
// 高さは本文の行送り(1行=1.0)を単位にする。
object ReviewPageLayout {

    // scaleは本文に対する文字サイズの比(jsbook.clsの\Huge=25pt、\huge=20pt、\Large=14.4pt、\large=12pt)
    enum class Style(val scale: Double, val bold: Boolean = false) {
        BODY(1.0),
        CODE(1.0),
        CAPTION(1.0),
        CHAPTER_NUMBER(2.0, true),
        CHAPTER_TITLE(2.5, true),
        SECTION(1.44, true),
        SUBSECTION(1.2, true),
        HEADING(1.0, true),
        ;

        // ponytail: 見出しの行送りは文字サイズの0.75倍(LaTeXの行送り≒文字サイズ×1.2を本文の行送り16ptで割った値)で近似。
        // 正確にするならjsbook.clsの各サイズの\baselineskipを移植する
        val lineHeight: Double get() = max(1.0, scale * 0.75)
    }

    sealed interface Element

    // 行分割の対象になる文。firstIndent/hangIndentは全角何文字ぶん字下げするか。
    data class Text(val text: String, val firstIndent: Int = 0, val hangIndent: Int = 0, val style: Style = Style.BODY) : Element

    // 行分割しない1行(コードや表の行)。版面からはみ出しても折り返さない。
    data class Raw(val text: String, val style: Style = Style.BODY) : Element

    // 見出しや段落間の空き。ページ先頭では消える(TeXがページ先頭の垂直方向の空きを捨てるのと同じ)。
    data class Gap(val height: Double = 1.0) : Element

    // ページ先頭でも消えない空き(章見出しの\vspace*)。
    data class Space(val height: Double) : Element

    data object PageBreak : Element

    sealed interface Item {
        val height: Double
    }

    data class Line(val text: String, val style: Style) : Item {
        override val height: Double get() = style.lineHeight
    }

    // 図。ページをまたいで分割されないため、収まらなければ次のページへ送る。
    // widthRatioは版面の幅に対する図の幅の比。imageがnullなら画像が読めず、枠だけ描く。
    data class Figure(val id: String, val image: BufferedImage?, val widthRatio: Double, override val height: Double) : Element, Item

    // yはページ内の版面上端からの位置(行送り単位)
    data class Placed(val y: Double, val item: Item)

    private const val LINE_HEAD_FORBIDDEN = "、。，．・：；？！ー）」』】〕〉》｝］’”ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ々ゝゞヽヾ,.:;?!)]}"
    private const val LINE_END_FORBIDDEN = "（「『【〔〈《｛［‘“([{"
    private const val EPSILON = 1e-6

    fun paginate(elements: List<Element>, settings: ReviewPageSettings): List<List<Placed>> {
        val capacity = settings.linesPerPage.toDouble()
        val pages = mutableListOf(mutableListOf<Placed>())
        var y = 0.0
        fun newPage() {
            if (pages.last().isNotEmpty()) pages += mutableListOf<Placed>()
            y = 0.0
        }
        fun place(item: Item) {
            if (y + item.height > capacity + EPSILON && pages.last().isNotEmpty()) newPage()
            pages.last() += Placed(y, item)
            y += item.height
        }
        for (element in elements) {
            when (element) {
                is Text -> {
                    val maxWidth = floor(settings.charsPerLine * 2 / element.style.scale).toInt()
                    wrap(element, maxWidth).forEach { place(Line(it, element.style)) }
                }
                is Raw -> place(Line(element.text, element.style))
                is Figure -> place(element)
                is Gap -> if (pages.last().isNotEmpty()) y += element.height
                is Space -> y += element.height
                PageBreak -> newPage()
            }
        }
        return pages
    }

    fun width(c: Char): Int = if (c in ' '..'~' || c in '｡'..'ﾟ') 1 else 2

    fun width(s: String): Int = s.sumOf { width(it) }

    // ponytail: 禁則は「行頭禁則文字のぶら下げ」と「行末禁則文字の追い出し」だけ。
    // TeXの字間調整による追い込みは再現しない
    fun wrap(text: Text, maxWidth: Int): List<String> =
        text.text.split('\n').flatMapIndexed { index, segment ->
            wrapSegment(segment, if (index == 0) text.firstIndent else text.hangIndent, text.hangIndent, maxWidth)
        }

    private fun wrapSegment(segment: String, firstIndent: Int, hangIndent: Int, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder("　".repeat(firstIndent))
        var currentWidth = firstIndent * 2
        val lineStartWidth = hangIndent * 2

        fun breakLine() {
            var carry = ""
            if (current.isNotEmpty() && current.last() in LINE_END_FORBIDDEN && current.trim().length > 1) {
                carry = current.last().toString()
                current.setLength(current.length - 1)
            }
            lines += current.toString().trimEnd()
            current.setLength(0)
            current.append("　".repeat(hangIndent)).append(carry)
            currentWidth = lineStartWidth + width(carry)
        }

        for (token in tokenize(segment)) {
            val tokenWidth = width(token)
            val atLineStart = currentWidth == lineStartWidth && lines.isNotEmpty()
            when {
                token.isBlank() && atLineStart -> Unit
                currentWidth + tokenWidth <= maxWidth -> {
                    current.append(token); currentWidth += tokenWidth
                }
                token.length == 1 && token[0] in LINE_HEAD_FORBIDDEN -> {
                    current.append(token); currentWidth += tokenWidth
                }
                token.isBlank() -> breakLine()
                tokenWidth > maxWidth - lineStartWidth -> {
                    // 1行に収まらない長い英単語は文字単位で分割する
                    for (c in token) {
                        if (currentWidth + width(c) > maxWidth) breakLine()
                        current.append(c); currentWidth += width(c)
                    }
                }
                else -> {
                    breakLine()
                    current.append(token); currentWidth += tokenWidth
                }
            }
        }
        lines += current.toString().trimEnd()
        return lines
    }

    // 半角英数記号の連なりは単語として分割しない。それ以外(和文)は1文字ずつ分割可能。
    private val TOKEN = Regex("""[!-~]+| +|.""")

    private fun tokenize(s: String): List<String> = TOKEN.findAll(s).map { it.value }.toList()
}
