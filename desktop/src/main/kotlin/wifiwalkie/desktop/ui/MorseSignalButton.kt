package wifiwalkie.desktop.ui

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton

/** Button-shaped signal indicator; the local instance is also held to transmit. */
class MorseSignalButton(private val idleText: String, private val activeText: String) : JButton(idleText) {
    private val idleColor = Color(226, 235, 247)
    private val activeColor = Color(68, 155, 112)

    init {
        isFocusable = false
        isOpaque = true
        isContentAreaFilled = true
        font = font.deriveFont(Font.BOLD, 18f)
        preferredSize = Dimension(340, 130)
        setSignalActive(false)
    }

    fun setSignalActive(active: Boolean) {
        text = if (active) activeText else idleText
        background = if (active) activeColor else idleColor
        foreground = if (active) Color.WHITE else Color(36, 52, 72)
        border = if (active) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)
            )
        } else {
            BorderFactory.createCompoundBorder(
                BorderFactory.createRaisedBevelBorder(),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)
            )
        }
        repaint()
    }
}
