package com.nowsprinting.intellijreview.preview

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Figure
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Line
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Placed
import com.nowsprinting.intellijreview.preview.ReviewPageLayout.Style
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.Scrollable
import javax.swing.SwingConstants

// 計算済みのページを、紙面の縮尺どおりに縦に並べて描く。紙面の幅はペインの幅に合わせて拡縮する。
// 紙面は印刷物の見た目に寄せるため、IDEのテーマにかかわらず白地に黒字で描く。
class ReviewPreviewPanel : JComponent(), Scrollable {
    class Content(val settings: ReviewPageSettings, val pages: List<List<Placed>>)

    var content: Content? = null
        set(value) {
            field = value
            revalidate()
            repaint()
        }

    init {
        // 幅が変わると縮尺が変わり、全体の高さも変わる
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = revalidate()
        })
    }

    private fun zoom(settings: ReviewPageSettings): Double =
        ((width - 2 * MARGIN).coerceAtLeast(MIN_PAGE_WIDTH) / settings.paperWidthPt).coerceAtMost(MAX_ZOOM)

    private fun pageTop(index: Int, pageHeight: Double): Double = HEADER_HEIGHT + index * (pageHeight + PAGE_GAP)

    override fun getPreferredSize(): Dimension {
        val content = content ?: return Dimension(0, 0)
        val pageHeight = content.settings.paperHeightPt * zoom(content.settings)
        return Dimension(MIN_PAGE_WIDTH, (pageTop(content.pages.size, pageHeight) + MARGIN).toInt())
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.color = BACKGROUND
            g2.fillRect(0, 0, width, height)
            val content = content ?: return
            paintHeader(g2, content)
            val settings = content.settings
            val zoom = zoom(settings)
            val pageWidth = settings.paperWidthPt * zoom
            val pageHeight = settings.paperHeightPt * zoom
            val left = (width - pageWidth) / 2
            val clip = g2.clipBounds ?: Rectangle(0, 0, width, height)
            content.pages.forEachIndexed { index, page ->
                val top = pageTop(index, pageHeight)
                if (top > clip.maxY || top + pageHeight < clip.minY) return@forEachIndexed
                paintPage(g2, settings, page, index + 1, left, top, zoom)
            }
        } finally {
            g2.dispose()
        }
    }

    private fun paintHeader(g: Graphics2D, content: Content) {
        val settings = content.settings
        val options = if (settings.options.isBlank()) "" else " [${settings.options}]"
        g.color = UIUtil.getLabelForeground()
        g.font = JBFont.label()
        g.drawString(
            "${settings.documentClass}$options  ${settings.charsPerLine}字 × ${settings.linesPerPage}行  ${content.pages.size}ページ",
            MARGIN, HEADER_HEIGHT - JBUI.scale(10),
        )
    }

    private fun paintPage(g: Graphics2D, settings: ReviewPageSettings, page: List<Placed>, number: Int, left: Double, top: Double, zoom: Double) {
        val pageRect = Rectangle2D.Double(left, top, settings.paperWidthPt * zoom, settings.paperHeightPt * zoom)
        g.color = SHADOW
        g.fill(Rectangle2D.Double(pageRect.x + 3, pageRect.y + 3, pageRect.width, pageRect.height))
        g.color = Color.WHITE
        g.fill(pageRect)

        // ponytail: 版面は紙面の中央に置く。ノド・小口の左右差やhead_space(天)は反映していない
        val area = Rectangle2D.Double(
            left + (settings.paperWidthPt - settings.textWidthPt) / 2 * zoom,
            top + (settings.paperHeightPt - settings.textHeightPt) / 2 * zoom,
            settings.textWidthPt * zoom,
            settings.textHeightPt * zoom,
        )
        g.color = AREA_GUIDE
        g.stroke = BasicStroke(1f)
        g.draw(area)

        for (placed in page) {
            when (val item = placed.item) {
                is Line -> paintLine(g, settings, item, area, area.y + placed.y * settings.baselineskipPt * zoom, zoom)
                is Figure -> paintFigure(g, settings, item, area, area.y + placed.y * settings.baselineskipPt * zoom, zoom)
            }
        }

        g.color = Color.DARK_GRAY
        g.font = Font(Font.SERIF, Font.PLAIN, 1).deriveFont((settings.zwPt * zoom * 0.9).toFloat())
        val folio = number.toString()
        val folioWidth = g.fontMetrics.stringWidth(folio)
        val bottomMargin = pageRect.maxY - area.maxY
        g.drawString(folio, (pageRect.centerX - folioWidth / 2).toFloat(), (area.maxY + bottomMargin / 2).toFloat())
    }

    private fun paintLine(g: Graphics2D, settings: ReviewPageSettings, line: Line, area: Rectangle2D.Double, y: Double, zoom: Double) {
        val height = line.height * settings.baselineskipPt * zoom
        if (line.style == Style.CODE) {
            g.color = CODE_BACKGROUND
            g.fill(Rectangle2D.Double(area.x, y, area.width, height))
        }
        val em = settings.zwPt * line.style.scale * zoom
        g.font = Font(fontFamily(line.style), if (line.style.bold) Font.BOLD else Font.PLAIN, 1).deriveFont(em.toFloat())
        g.color = if (line.style == Style.CAPTION) CAPTION_COLOR else Color.BLACK
        // 字送りを行分割の計算(全角=1zw、半角=0.5zw)と揃え、版面のどこで折り返したかを見たとおりにする。
        // 半角の連なりを1文字ずつ0.5zwに置くと欧文フォントの字幅と合わず重なるため、連なりごとに横方向だけ伸縮させる。
        val baseline = y + height / 2 + em * 0.38
        val text = line.text
        var x = area.x
        var start = 0
        while (start < text.length) {
            val halfWidth = ReviewPageLayout.width(text[start]) == 1
            var end = start + 1
            if (halfWidth) while (end < text.length && ReviewPageLayout.width(text[end]) == 1) end++
            val run = text.substring(start, end)
            val runWidth = ReviewPageLayout.width(run) * em / 2
            val naturalWidth = g.fontMetrics.getStringBounds(run, g).width
            if (halfWidth && naturalWidth > 0) {
                val saved = g.transform
                g.translate(x, baseline)
                g.scale(runWidth / naturalWidth, 1.0)
                g.drawString(run, 0f, 0f)
                g.transform = saved
            } else {
                g.drawString(run, x.toFloat(), baseline.toFloat())
            }
            x += runWidth
            start = end
        }
        if (x > area.maxX + 0.5) {
            // 折り返さないコードや表の行が版面からはみ出していることを示す
            g.color = OVERFLOW_MARK
            g.stroke = BasicStroke(2f)
            g.draw(Line2D.Double(area.maxX + 2, y, area.maxX + 2, y + height))
        }
    }

    private fun paintFigure(g: Graphics2D, settings: ReviewPageSettings, figure: Figure, area: Rectangle2D.Double, y: Double, zoom: Double) {
        val width = area.width * figure.widthRatio
        val rect = Rectangle2D.Double(area.x + (area.width - width) / 2, y, width, figure.height * settings.baselineskipPt * zoom)
        val image = figure.image
        if (image != null) {
            g.drawImage(image, rect.x.toInt(), rect.y.toInt(), rect.width.toInt(), rect.height.toInt(), null)
            return
        }
        g.color = PLACEHOLDER_BACKGROUND
        g.fill(rect)
        g.color = Color.GRAY
        g.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 4f), 0f)
        g.draw(rect)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont((settings.zwPt * zoom).toFloat())
        val label = figure.id
        g.drawString(label, (rect.centerX - g.fontMetrics.stringWidth(label) / 2).toFloat(), rect.centerY.toFloat())
    }

    // jsbook.clsは本文を明朝、見出しをゴシック(\headfont)で組む
    private fun fontFamily(style: Style): String = when (style) {
        Style.BODY, Style.CAPTION -> Font.SERIF
        Style.CODE -> Font.MONOSPACED
        else -> Font.SANS_SERIF
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    companion object {
        private val MARGIN = JBUI.scale(16)
        private val HEADER_HEIGHT = JBUI.scale(36)
        private val PAGE_GAP = JBUI.scale(16)
        private val MIN_PAGE_WIDTH = JBUI.scale(200)
        private const val MAX_ZOOM = 2.0

        private val BACKGROUND = JBColor(Color(0xD4D4D4), Color(0x2B2D30))
        private val SHADOW = Color(0, 0, 0, 60)
        private val AREA_GUIDE = Color(0xE4E8F0)
        private val CODE_BACKGROUND = Color(0xF2F2F2)
        private val CAPTION_COLOR = Color(0x333333)
        private val PLACEHOLDER_BACKGROUND = Color(0xF5F5F5)
        private val OVERFLOW_MARK = Color(0xE05050)
    }
}
