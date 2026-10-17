package wifiwalkie.desktop.ui

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JComponent

/** Live press duration with one-unit dot and three-unit dash target marks. */
class PressDurationGauge : JComponent() {
    private var elapsedMs = 0L

    init {
        preferredSize = Dimension(190, 180)
        minimumSize = Dimension(170, 150)
        border = BorderFactory.createTitledBorder("Press length")
        toolTipText = "Aim for 180 ms for a dot or 540 ms for a dash"
    }

    fun setElapsed(milliseconds: Long) {
        elapsedMs = milliseconds.coerceAtLeast(0)
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val top = 28
        val bottom = height - 38
        val trackX = width - 24
        val trackWidth = 12
        val usableHeight = (bottom - top).coerceAtLeast(1)
        val maxMs = 900L
        val fillHeight = (elapsedMs.coerceAtMost(maxMs) * usableHeight / maxMs).toInt()

        g.color = Color(232, 237, 244)
        g.fillRoundRect(trackX, top, trackWidth, usableHeight, 8, 8)
        g.color = if (elapsedMs < PressDurationGauge.DASH_THRESHOLD_MS) Color(63, 137, 202) else Color(224, 148, 52)
        g.fillRoundRect(trackX, bottom - fillHeight, trackWidth, fillHeight, 8, 8)

        g.font = font.deriveFont(Font.PLAIN, 12f)
        drawMark(g, "DOT  180 ms", PressDurationGauge.DOT_TARGET_MS, top, bottom, maxMs, trackX)
        drawMark(g, "DASH  540 ms", PressDurationGauge.DASH_TARGET_MS, top, bottom, maxMs, trackX)

        g.color = Color(35, 48, 65)
        g.font = font.deriveFont(Font.BOLD, 15f)
        val elapsedText = if (elapsedMs >= 1000) "%.1f s".format(elapsedMs / 1000.0) else "$elapsedMs ms"
        g.drawString(elapsedText, 10, height - 14)
        g.dispose()
    }

    private fun drawMark(g: Graphics2D, label: String, milliseconds: Long, top: Int, bottom: Int, maxMs: Long, trackX: Int) {
        val y = bottom - (milliseconds * (bottom - top) / maxMs).toInt()
        g.color = Color(83, 96, 112)
        g.drawLine(trackX - 5, y, trackX + 16, y)
        g.drawString(label, 10, y + 4)
    }

    companion object {
        const val DOT_TARGET_MS = 180L
        const val DASH_TARGET_MS = 540L
        const val DASH_THRESHOLD_MS = 360L
    }
}
