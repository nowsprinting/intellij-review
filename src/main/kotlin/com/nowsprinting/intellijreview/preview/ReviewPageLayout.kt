package com.nowsprinting.intellijreview.preview

import java.awt.image.BufferedImage
import kotlin.math.max

// 版面(1行の字数・1ページの行数)に沿って、行分割と改ページを計算する。
// 幅はその行の文字サイズの全角幅(zw)を単位にし、欧文はLatin Modernの字幅で数える。
// 高さは本文の行送り(1行=1.0)を単位にする。
object ReviewPageLayout {

    // scaleは本文に対する文字サイズの比(jsbook.clsの\Huge=25pt、\huge=20pt、\Large=14.4pt、\large=12pt、
    // \small=9pt、\footnotesize=8pt)
    enum class Style(val scale: Double, val bold: Boolean = false) {
        BODY(1.0),
        CODE(0.9),

        // \reviewlistcaptionなど、左寄せのキャプション
        CAPTION(0.9),

        // 図表の\caption。jsbook.clsの\@makecaptionは1行に収まるキャプションを中央に置く
        FLOAT_CAPTION(0.9),
        FOOTNOTE(0.8),
        CHAPTER_NUMBER(2.0, true),
        CHAPTER_TITLE(2.5, true),
        SECTION(1.44, true),
        SUBSECTION(1.2, true),
        HEADING(1.0, true),
    }

    // NOTE_MARKは脚注番号(\@makefnmarkの上付き文字)
    enum class Face { ROMAN, BOLD, TT, NOTE_MARK }

    // 書体を切り替えるインライン命令の範囲を、Textの文字列に埋め込む目印(私用領域の文字)
    // ponytail: 入れ子や@<br>をまたぐ範囲は扱わない。必要になったらTextを書体付きの区間の列にする
    const val BOLD_START = ''
    const val BOLD_END = ''
    const val TT_START = ''
    const val TT_END = ''
    const val NOTE_START = ''
    const val NOTE_END = ''

    fun isMarker(c: Char): Boolean = c in BOLD_START..NOTE_END

    sealed interface Element

    // 行分割の対象になる文。firstIndent/hangIndentは全角何文字ぶん字下げするか。
    // offsetは原稿(.reファイル)中のこの要素の位置で、エディタとのスクロールの対応付けに使う。原稿に対応しないものは-1
    data class Text(val text: String, val firstIndent: Int = 0, val hangIndent: Int = 0, val style: Style = Style.BODY, val offset: Int = -1) : Element

    // 行分割しない1行(コードや表の行)。版面からはみ出しても折り返さない。
    data class Raw(val text: String, val style: Style = Style.BODY, val offset: Int = -1) : Element

    // 見出しや段落間の空き。ページ先頭では消える(TeXがページ先頭の垂直方向の空きを捨てるのと同じ)。
    data class Gap(val height: Double = 1.0) : Element

    // ページ先頭でも消えない空き(章見出しの\vspace*)。
    data class Space(val height: Double) : Element

    data object PageBreak : Element

    // 脚注の本文。markは本文中の脚注番号("*1"など)で、その番号を含む行と同じページの下部に組む。
    data class Footnote(val mark: String, val text: String) : Element

    sealed interface Item {
        val height: Double
    }

    // 行分割と描画で共有する字送りの単位。widthはその行の文字サイズのzw単位。
    // glueは欧文の語間やxkanjiskipで、改行位置になり、行頭・行末では消える。
    data class Token(val text: String, val face: Face, val width: Double, val glue: Boolean = false)

    // indentは行頭の位置(その行の文字サイズのzw単位)
    data class Line(val tokens: List<Token>, val style: Style, override val height: Double, val indent: Double = 0.0) : Item {
        val text: String get() = tokens.joinToString("") { it.text }

        // 行末の句読点のアキは行末で消えるため、見た目の幅に含めない
        val width: Double get() = indent + tokens.sumOf { it.width } - (tokens.lastOrNull()?.let { trailingSpace(it) } ?: 0.0)
    }

    // 図。ページをまたいで分割されないため、収まらなければ次のページへ送る。
    // widthRatioは版面の幅に対する図の幅の比。imageがnullなら画像が読めず、枠だけ描く。
    data class Figure(val id: String, val image: BufferedImage?, val widthRatio: Double, override val height: Double, val offset: Int = -1) : Element, Item

    // コードの網掛け(framed.styのshaded)の上下の内側の余白
    data class Padding(override val height: Double) : Element, Item

    // 脚注の上の区切り線(\footnoterule)
    data object FootnoteRule : Item {
        override val height: Double get() = 0.0
    }

    // yはページ内の版面上端からの位置(行送り単位)。offsetは元のElementの原稿中の位置(脚注など対応しないものは-1)
    data class Placed(val y: Double, val item: Item, val offset: Int = -1)

    // ponytail: 小書きの仮名(ァ・ッなど)は行頭に置けるものとし、禁則にしない
    private const val LINE_HEAD_FORBIDDEN = "、。，．・：；？！ー）」』】〕〉》｝］’”々ゝゞヽヾ,.:;?!)]}"
    private const val LINE_END_FORBIDDEN = "（「『【〔〈《｛［‘“([{"

    // jsbook.clsの和文フォントメトリックで、二分のアキを前(OPENING)・後ろ(CLOSING)に持つ約物
    const val OPENING = "「『（【〔〈《｛［“‘"
    private const val CLOSING = "」』）】〕〉》｝］”’、。，．"

    // jsbook.clsの\skip\footins(16pt)と、\footnoteruleを脚注の上端からどれだけ上に引くか(\kern-3pt)
    private const val FOOTINS_SKIP_PT = 16.0
    private const val FOOTNOTE_RULE_RAISE_PT = 3.0

    // jsbook.clsの\@makefntext: 脚注の本文は3zw下げ、番号はその手前に0.3zw空けて置く
    private const val FOOTNOTE_INDENT = 3.0
    private const val FOOTNOTE_MARK_GAP = 0.3

    // \@textsuperscriptの文字サイズ(\sf@size=7pt)の本文(10pt)に対する比
    const val SUPERSCRIPT_SCALE = 0.7

    private const val EPSILON = 1e-6

    fun lineHeight(style: Style, settings: ReviewPageSettings): Double {
        val pt = when (style) {
            Style.BODY -> return 1.0
            // review-base.styのreviewlistは\smallの中で\baselineskip=1.3zw
            Style.CODE -> 1.3 * style.scale * settings.zwPt
            // jsbook.clsの\smallと\footnotesizeの行送り
            Style.CAPTION, Style.FLOAT_CAPTION -> 13 * settings.fontScale
            Style.FOOTNOTE -> 11 * settings.fontScale
            // ponytail: 見出しの行送りは文字サイズの0.75倍(LaTeXの行送り≒文字サイズ×1.2を本文の行送り16ptで割った値)で近似。
            // 正確にするならjsbook.clsの各サイズの\baselineskipを移植する
            else -> return max(1.0, style.scale * 0.75)
        }
        return pt / settings.baselineskipPt
    }

    fun paginate(elements: List<Element>, settings: ReviewPageSettings): List<List<Placed>> {
        class Note(val mark: Line, val lines: List<Line>)

        val capacity = settings.linesPerPage.toDouble()
        val footnotes = elements.filterIsInstance<Footnote>().associateBy { it.mark }
        val pages = mutableListOf(mutableListOf<Placed>())
        val pageNotes = mutableListOf<Note>()
        var y = 0.0
        // 直前に置いたGapの高さ。Gapが続いたら大きいほうだけ取る(TeXの\addvspace)
        var lastGap = 0.0

        fun note(footnote: Footnote): Note {
            val style = Style.FOOTNOTE
            val height = lineHeight(style, settings)
            val lines = wrap(Text(footnote.text, style = style), settings, settings.charsPerLine / style.scale - FOOTNOTE_INDENT)
                .map { Line(it, style, height, FOOTNOTE_INDENT) }
            val mark = tokenize("$NOTE_START${footnote.mark}$NOTE_END", style, settings)
            return Note(Line(mark, style, height, FOOTNOTE_INDENT - FOOTNOTE_MARK_GAP - mark.sumOf { it.width }), lines)
        }
        fun notesHeight(notes: List<Note>): Double =
            if (notes.isEmpty()) 0.0 else FOOTINS_SKIP_PT * settings.fontScale / settings.baselineskipPt + notes.sumOf { n -> n.lines.sumOf { it.height } }
        fun flushNotes() {
            if (pageNotes.isEmpty()) return
            var noteY = capacity - pageNotes.sumOf { n -> n.lines.sumOf { it.height } }
            pages.last() += Placed(noteY - FOOTNOTE_RULE_RAISE_PT * settings.fontScale / settings.baselineskipPt, FootnoteRule)
            for (n in pageNotes) {
                pages.last() += Placed(noteY, n.mark)
                n.lines.forEach { pages.last() += Placed(noteY, it); noteY += it.height }
            }
            pageNotes.clear()
        }
        fun newPage() {
            flushNotes()
            if (pages.last().isNotEmpty()) pages += mutableListOf<Placed>()
            y = 0.0
        }
        // 脚注はその番号を含む行と同じページに組むため、ページの残りから脚注の高さを差し引いて収まるかを見る
        fun place(item: Item, offset: Int = -1) {
            val notes = (item as? Line)?.tokens.orEmpty().filter { it.face == Face.NOTE_MARK }.mapNotNull { footnotes[it.text] }.map(::note)
            if (y + item.height + notesHeight(pageNotes + notes) > capacity + EPSILON && pages.last().isNotEmpty()) newPage()
            pages.last() += Placed(y, item, offset)
            y += item.height
            lastGap = 0.0
            pageNotes += notes
        }
        for (element in elements) {
            when (element) {
                is Text -> {
                    val lines = wrap(element, settings)
                    val style = if (element.style == Style.FLOAT_CAPTION && lines.size > 1) Style.CAPTION else element.style
                    lines.forEach { place(Line(it, style, lineHeight(style, settings)), element.offset) }
                }
                is Raw -> place(Line(tokenize(element.text, element.style, settings), element.style, lineHeight(element.style, settings)), element.offset)
                is Figure -> place(element, element.offset)
                is Padding -> place(element)
                is Gap -> if (pages.last().isNotEmpty() && element.height > lastGap) {
                    y += element.height - lastGap
                    lastGap = element.height
                }
                is Space -> { y += element.height; lastGap = 0.0 }
                is Footnote -> Unit
                PageBreak -> newPage()
            }
        }
        flushNotes()
        // 章は奇数ページから始まるため、奇数ページで終わる章の後には白ページ(ノンブルは付く)が入る
        if (!settings.openAny && pages.size % 2 == 1) pages += mutableListOf<Placed>()
        return pages
    }

    // ---- 字幅 ----

    // 半角英数記号の連なり・欧文の語間・目印・それ以外の1文字
    private val TOKEN = Regex("""[!-~]+| +|[-]|.""")

    // jsbook.clsの\xkanjiskip(0.25em)
    private const val XKANJISKIP_EM = 0.25

    // Latin Modern(lmr10・lmbx10)の字幅(1/1000em)。文字コード32(空白)〜126の順。
    // review-base.styがlmodernを読み込むため、欧文はこのフォントで組まれる。合字とカーニングは無視している
    private val LMR10 = intArrayOf(
        333, 278, 374, 833, 500, 833, 778, 278, 389, 389, 500, 778, 278, 333, 278, 500, 500, 500, 500, 500, 500, 500, 500, 500,
        500, 500, 278, 278, 778, 778, 778, 472, 778, 750, 708, 722, 764, 681, 653, 785, 750, 361, 514, 778, 625, 917, 750, 778,
        681, 778, 736, 556, 722, 750, 750, 1028, 750, 750, 611, 278, 500, 278, 556, 750, 500, 500, 556, 444, 556, 444, 306, 500,
        556, 278, 306, 528, 278, 833, 556, 500, 556, 528, 392, 394, 389, 556, 528, 722, 528, 528, 444, 500, 278, 500, 556,
    )
    private val LMBX10 = intArrayOf(
        383, 350, 481, 958, 575, 958, 894, 319, 447, 447, 575, 894, 319, 383, 319, 575, 575, 575, 575, 575, 575, 575, 575, 575,
        575, 575, 319, 319, 894, 894, 894, 543, 894, 869, 818, 831, 882, 756, 724, 904, 900, 436, 594, 901, 692, 1092, 900, 864,
        786, 864, 862, 639, 800, 885, 869, 1189, 869, 869, 703, 319, 575, 319, 556, 869, 575, 559, 639, 511, 639, 527, 351, 575,
        639, 319, 351, 607, 319, 958, 639, 575, 639, 607, 474, 454, 447, 639, 607, 831, 607, 607, 511, 575, 319, 575, 556,
    )

    // lmtt10は等幅で、どの文字も0.525em
    private const val LMTT10 = 525

    private fun latinWidth(c: Char, face: Face): Double = when (face) {
        Face.ROMAN -> LMR10[c - ' ']
        Face.BOLD -> LMBX10[c - ' ']
        Face.TT -> LMTT10
        Face.NOTE_MARK -> LMR10[c - ' '] * SUPERSCRIPT_SCALE
    }.toDouble() / 1000

    private fun isLatin(c: Char): Boolean = c in ' '..'~'

    // xkanjiskipを入れる和文側の文字(仮名・漢字など)。約物には入れない
    private fun isKanji(c: Char): Boolean = !isLatin(c) && !isMarker(c) && c.isLetter()

    private fun isAlnum(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private fun trailingSpace(token: Token): Double = if (token.width == 1.0 && token.text.length == 1 && token.text[0] in CLOSING) 0.5 else 0.0

    fun tokenize(text: String, style: Style, settings: ReviewPageSettings): List<Token> {
        val latinEm = settings.latinEmPt / settings.zwPt
        val code = style == Style.CODE
        val tokens = mutableListOf<Token>()
        var bold = false
        var tt = code
        var note = false
        for (match in TOKEN.findAll(text)) {
            val s = match.value
            when (s[0]) {
                BOLD_START -> { bold = true; continue }
                BOLD_END -> { bold = false; continue }
                TT_START -> { tt = true; continue }
                TT_END -> { tt = code; continue }
                NOTE_START -> { note = true; continue }
                NOTE_END -> { note = false; continue }
            }
            val face = when {
                note -> Face.NOTE_MARK
                tt -> Face.TT
                bold -> Face.BOLD
                else -> Face.ROMAN
            }
            val prev = tokens.lastOrNull()
            val prevChar = prev?.text?.lastOrNull()
            // コードブロックではreview-style.styが\xkanjiskipを0にする。脚注番号は箱に入るため前後に入らない
            val xkanjiskip = !code && face != Face.NOTE_MARK && prev != null && prev.face != Face.NOTE_MARK && prevChar != null &&
                (isKanji(prevChar) && isAlnum(s[0]) || isAlnum(prevChar) && isKanji(s[0]))
            if (xkanjiskip) tokens += Token("", face, XKANJISKIP_EM * latinEm, glue = true)
            tokens += when {
                s[0] == ' ' -> listOf(Token(s, face, s.length * latinWidth(' ', face) * latinEm, glue = !code))
                // \reviewtt・\reviewcodeは任意の文字間で改行できる(review-base.styの\reviewbreakall)
                isLatin(s[0]) && face == Face.TT && !code -> s.map { Token(it.toString(), face, latinWidth(it, face) * latinEm) }
                isLatin(s[0]) -> listOf(Token(s, face, s.sumOf { latinWidth(it, face) } * latinEm))
                else -> listOf(Token(s, face, japaneseWidth(s[0], if (prev?.glue == false) prevChar else null)))
            }
        }
        return tokens
    }

    // 約物が続くと、jsbook.clsの和文フォントメトリックは間の二分のアキを詰める(「、「」は1.5字)
    private fun japaneseWidth(c: Char, prev: Char?): Double = when {
        c in '｡'..'ﾟ' -> 0.5
        prev != null && (c in OPENING || c in CLOSING) && (prev in CLOSING || prev in OPENING && c in OPENING) -> 0.5
        else -> 1.0
    }

    // ---- 行分割 ----

    fun wrap(text: Text, settings: ReviewPageSettings, maxWidth: Double = settings.charsPerLine / text.style.scale): List<List<Token>> =
        text.text.split('\n').flatMapIndexed { index, segment ->
            wrapTokens(tokenize(segment, text.style, settings), if (index == 0) text.firstIndent else text.hangIndent, text.hangIndent, maxWidth)
        }

    private fun Token.isHeadForbidden(): Boolean = text.length == 1 && text[0] in LINE_HEAD_FORBIDDEN
    private fun Token.isEndForbidden(): Boolean = text.length == 1 && text[0] in LINE_END_FORBIDDEN

    private fun indent(n: Int): List<Token> = if (n > 0) listOf(Token("　".repeat(n), Face.ROMAN, n.toDouble())) else emptyList()

    // ponytail: TeXの段落単位の最適化や、約物のアキを詰める追い込みは再現せず、先頭から貪欲に詰める。
    // 行頭禁則文字が溢れたら、直前の文字ごと次の行へ追い出す(jsbook.clsはぶら下げをしない)
    private fun wrapTokens(tokens: List<Token>, firstIndent: Int, hangIndent: Int, maxWidth: Double): List<List<Token>> {
        val lines = mutableListOf<List<Token>>()
        var current = indent(firstIndent).toMutableList()
        var start = current.size

        fun width() = current.sumOf { it.width }
        fun newLine(carry: List<Token>) {
            while (current.size > start && current.last().glue) current.removeLast()
            lines += current
            current = indent(hangIndent).toMutableList()
            start = current.size
            current += carry
        }
        // 追い出す範囲の先頭。行頭禁則文字が続けばその前の文字から、直前が行末禁則文字ならそれも含める
        fun pushOutFrom(): Int? {
            var i = current.size
            while (i > start && current[i - 1].glue) i--
            do { i-- } while (i > start && current[i].isHeadForbidden())
            while (i > start && current[i - 1].isEndForbidden()) i--
            return i.takeIf { it > start }
        }
        fun add(token: Token) {
            val atLineStart = current.size == start
            when {
                token.glue -> if (!atLineStart) current += token
                width() + token.width - trailingSpace(token) <= maxWidth + EPSILON -> current += token
                atLineStart && token.text.length > 1 -> {
                    // 1行に収まらない長い英単語は文字単位で分割する
                    val charWidth = token.width / token.text.length
                    token.text.forEach { c ->
                        if (current.size > start && width() + charWidth > maxWidth + EPSILON) newLine(emptyList())
                        current += Token(c.toString(), token.face, charWidth)
                    }
                }
                atLineStart -> current += token
                token.isHeadForbidden() -> {
                    val from = pushOutFrom()
                    if (from == null) {
                        current += token
                    } else {
                        val carry = current.subList(from, current.size).toList()
                        repeat(carry.size) { current.removeLast() }
                        newLine(carry + token)
                    }
                }
                current.last().isEndForbidden() && current.size > start + 1 -> newLine(listOf(current.removeLast(), token))
                else -> {
                    newLine(emptyList())
                    add(token)
                }
            }
        }
        tokens.forEach(::add)
        while (current.size > start && current.last().glue) current.removeLast()
        lines += current
        return lines
    }
}
