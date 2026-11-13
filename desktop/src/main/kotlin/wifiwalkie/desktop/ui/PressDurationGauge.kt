package wifiwalkie.desktop.ui

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JComponent
import wifiwalkie.core.morse.MorseDecoder

/** Live press duration, with one line at the decoder's dot/dash cutoff. */
class PressDurationGauge : JComponent() {
    private var elapsedMs = 0L

    init {
        preferredSize = Dimension(190, 180)
        minimumSize = Dimension(170, 150)
        border = BorderFactory.createTitledBorder("Press length")
        toolTipText = "Release below the line ($DASH_THRESHOLD_MS ms) for a dot, above it for a dash"
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
        g.color = if (elapsedMs < DASH_THRESHOLD_MS) Color(63, 137, 202) else Color(224, 148, 52)
        g.fillRoundRect(trackX, bottom - fillHeight, trackWidth, fillHeight, 8, 8)

        // The one cutoff: release below the line for a dot, above it for a dash.
        val lineY = bottom - (DASH_THRESHOLD_MS * usableHeight / maxMs).toInt()
        g.color = Color(83, 96, 112)
        g.drawLine(trackX - 5, lineY, trackX + 16, lineY)
        g.font = font.deriveFont(Font.PLAIN, 12f)
        g.color = Color(224, 148, 52).darker()
        g.drawString("▲ DASH", 10, lineY - 6)
        g.color = Color(63, 137, 202).darker()
        g.drawString("▼ DOT", 10, lineY + 16)
        g.color = Color(83, 96, 112)
        g.drawString("$DASH_THRESHOLD_MS ms", 70, lineY + 4)

        g.color = Color(35, 48, 65)
        g.font = font.deriveFont(Font.BOLD, 15f)
        val elapsedText = if (elapsedMs >= 1000) "%.1f s".format(elapsedMs / 1000.0) else "$elapsedMs ms"
        g.drawString(elapsedText, 10, height - 14)
        g.dispose()
    }

    companion object {
        /** Taken from the decoder, so the line always matches how presses are decoded. */
        const val DASH_THRESHOLD_MS = MorseDecoder.DEFAULT_UNIT_MS * MorseDecoder.DASH_THRESHOLD_UNITS
    }
}
