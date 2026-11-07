package com.nowsprinting.intellijreview.preview

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// config.ymlのtexdocumentclassから、PDF出力時の版面(1行の字数・1ページの行数)を見積もる。
// TeXを実行せずに済ませるため、review-jsbook.cls/jsbook.cls・review-jlreq.clsの寸法計算式を
// 必要な部分だけ移植している(引用元はkmuto/reviewのtemplates/latex、texjporg/jsclassesのjsclasses.dtx)。
// 寸法(paper*・zw・baselineskip)の単位はpt。プレビューペインで紙面を縮尺どおりに描くために使う。
data class ReviewPageSettings(
    val documentClass: String,
    val options: String,
    val charsPerLine: Int,
    val linesPerPage: Int,
    val paperWidthPt: Double,
    val paperHeightPt: Double,
    val zwPt: Double,
    val baselineskipPt: Double,
) {
    val textWidthPt: Double get() = charsPerLine * zwPt
    val textHeightPt: Double get() = linesPerPage * baselineskipPt

    companion object {
        // texdocumentclass未指定時のRe:VIEW既定値(lib/review/configure.rb)
        val DEFAULT_CLASS = listOf("review-jsbook", "")

        private const val PT_PER_MM = 72.27 / 25.4

        // jsbook.clsは10ptでも和文フォントを0.924690倍(\Cjascale)して組むため、1zwは10ptにならない
        private const val JSBOOK_ZW_PT = 9.2469

        private val PAPER_MM = mapOf(
            "a3" to (297.0 to 420.0), "a4" to (210.0 to 297.0), "a5" to (148.0 to 210.0), "a6" to (105.0 to 148.0),
            "b4" to (257.0 to 364.0), "b5" to (182.0 to 257.0), "b6" to (128.0 to 182.0),
            "a4var" to (210.0 to 283.0), "b5var" to (182.0 to 230.0),
            "letter" to (215.9 to 279.4), "legal" to (215.9 to 355.6), "executive" to (184.15 to 266.7),
        )

        // jlreqのb4〜b6はISO B列で、JIS B列は"b5j"のように末尾jで指定する(jsbookのb5はJIS B列)
        private val JLREQ_PAPER_MM = PAPER_MM + mapOf(
            "b4" to (250.0 to 353.0), "b5" to (176.0 to 250.0), "b6" to (125.0 to 176.0),
            "b4j" to (257.0 to 364.0), "b5j" to (182.0 to 257.0), "b6j" to (128.0 to 182.0),
        )

        // jsbook.clsの文字サイズオプションと\jsc@magscale。review-jsbookはこれ以外のサイズを
        // 近いものに丸めて渡す(review-jsbook.clsの\recls@set@customfontsize)。
        private val JSBOOK_MAGSCALE = mapOf(
            "8pt" to 0.833, "9pt" to 0.913, "10pt" to 1.0, "11pt" to 1.095, "12pt" to 1.2, "14pt" to 1.44,
            "17pt" to 1.728, "20pt" to 2.0, "21pt" to 2.074, "25pt" to 2.488, "30pt" to 2.986, "36pt" to 3.583,
            "43pt" to 4.3, "12Q" to 0.923, "14Q" to 1.077, "10ptj" to 1.085, "10.5ptj" to 1.139,
            "11ptj" to 1.194, "12ptj" to 1.302,
        )
        private val JSBOOK_CUSTOM_FONTSIZE_THRESHOLDS = listOf(
            8.5 to "8pt", 9.5 to "9pt", 10.5 to "10pt", 11.5 to "11pt", 12.5 to "12pt", 13.0 to "14pt",
            18.5 to "17pt", 20.5 to "20pt", 23.0 to "21pt", 27.5 to "25pt", 33.0 to "30pt", 39.5 to "36pt",
        )

        fun from(texDocumentClass: List<String>?): ReviewPageSettings {
            val documentClass = texDocumentClass?.getOrNull(0) ?: DEFAULT_CLASS[0]
            val options = texDocumentClass?.getOrNull(1) ?: DEFAULT_CLASS[1]
            val parsed = parseOptions(options)
            val settings = if (documentClass.contains("jlreq")) jlreq(parsed) else jsbook(parsed)
            return settings.copy(
                documentClass = documentClass,
                options = options,
                charsPerLine = max(settings.charsPerLine, 1),
                linesPerPage = max(settings.linesPerPage, 1),
            )
        }

        // Re:VIEW 2系のQ=/W=/L=は、review-updateと同じ規則で現行のオプション名に読み替える
        // (lib/review/update.rb)。
        private fun parseOptions(options: String): Map<String, String> =
            options.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associate { option ->
                val key = option.substringBefore('=').trim()
                val value = option.substringAfter('=', "").trim()
                when (key) {
                    "Q" -> "fontsize" to "${value}Q"
                    "W" -> "line_length" to "${value}zw"
                    "L" -> "number_of_lines" to value
                    else -> key to value
                }
            }

        private fun jsbook(options: Map<String, String>): ReviewPageSettings {
            val fontsize = options["fontsize"] ?: "10pt"
            val scale = JSBOOK_MAGSCALE[fontsize] ?: run {
                val pt = toPt(fontsize, 10.0) ?: 10.0
                JSBOOK_MAGSCALE.getValue(JSBOOK_CUSTOM_FONTSIZE_THRESHOLDS.firstOrNull { pt < it.first }?.second ?: "43pt")
            }
            val zw = JSBOOK_ZW_PT * scale
            val baselineskip = options["baselineskip"]?.let { toPt(it, zw) }
                ?: options["linegap"]?.let { toPt(it, zw) }?.let { zw + it }
                ?: (16.0 * scale)
            val (paperWidth, paperHeight) = paperPt(options, PAPER_MM, "a4")

            // \dimexpr の除算は切り捨てでなく四捨五入
            val chars = options["line_length"]?.let { toPt(it, zw) }?.let { (it / zw).roundToInt() }
                ?: min(40, floor((paperWidth - 36 * PT_PER_MM * scale) / zw).toInt())

            // ponytail: jsbook.cls(book・非report)の\textheight既定値の式。headheight・headsep等を
            // 個別指定した場合の変化は追っていない。必要になったらjsclasses.dtxの\textheight節を移植する
            val lines = options["number_of_lines"]?.toIntOrNull() ?: run {
                val topskip = 1.38 * zw
                val footskip = max(0.03367 * paperHeight, baselineskip)
                val headsep = 6 * PT_PER_MM * scale - topskip + 10 * scale
                val body = 0.83 * paperHeight - 10 * scale - headsep - footskip - topskip
                floor(body / baselineskip).toInt() + 1
            }
            return ReviewPageSettings("", "", chars, lines, paperWidth, paperHeight, zw, baselineskip)
        }

        // ponytail: jlreqの既定版面は「紙面の0.75倍」で近似。jlreq本体の細かな丸めは追っていない
        private fun jlreq(options: Map<String, String>): ReviewPageSettings {
            val zw = options["fontsize"]?.let { toPt(it, 10.0) } ?: 10.0
            val baselineskip = options["baselineskip"]?.let { toPt(it, zw) }
                ?: options["line_gap"]?.let { toPt(it, zw) }?.let { zw + it }
                ?: (1.7 * zw)
            val (paperWidth, paperHeight) = paperPt(options, JLREQ_PAPER_MM, "a5")
            val chars = options["line_length"]?.let { toPt(it, zw) }?.let { floor(it / zw + 1e-6).toInt() }
                ?: floor(0.75 * paperWidth / zw).toInt()
            val lines = options["number_of_lines"]?.toIntOrNull()
                ?: (floor((0.75 * paperHeight - zw) / baselineskip).toInt() + 1)
            return ReviewPageSettings("", "", chars, lines, paperWidth, paperHeight, zw, baselineskip)
        }

        private fun paperPt(options: Map<String, String>, papers: Map<String, Pair<Double, Double>>, default: String): Pair<Double, Double> {
            val (w, h) = papers[options["paper"]?.lowercase()] ?: papers.getValue(default)
            val customWidth = options["paperwidth"]?.let { toPt(it, 10.0) }
            val customHeight = options["paperheight"]?.let { toPt(it, 10.0) }
            return (customWidth ?: (w * PT_PER_MM)) to (customHeight ?: (h * PT_PER_MM))
        }

        private val LENGTH = Regex("""^([0-9]*\.?[0-9]+)\s*(zw|zh|Q|H|mm|cm|in|bp|pt|truemm|truept)?$""")

        // TeXの寸法をptに換算する。zw(全角幅)は呼び出し側の和文フォントサイズで換算する。
        fun toPt(length: String, zwPt: Double): Double? {
            val match = LENGTH.matchEntire(length.trim()) ?: return null
            val value = match.groupValues[1].toDouble()
            return value * when (match.groupValues[2]) {
                "zw", "zh" -> zwPt
                "Q", "H" -> 0.25 * PT_PER_MM
                "mm", "truemm" -> PT_PER_MM
                "cm" -> 10 * PT_PER_MM
                "in" -> 72.27
                "bp" -> 72.27 / 72
                else -> 1.0
            }
        }
    }
}
