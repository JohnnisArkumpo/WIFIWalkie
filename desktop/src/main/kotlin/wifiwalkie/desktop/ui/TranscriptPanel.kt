package wifiwalkie.desktop.ui

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class TranscriptPanel(title: String) : JPanel(BorderLayout(6, 6)) {
    private val transcript = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    }
    private val visibility = JButton("Hide")
    private var hidden = false

    init {
        border = BorderFactory.createTitledBorder(title)
        preferredSize = Dimension(300, 120)
        add(JScrollPane(transcript), BorderLayout.CENTER)
        add(visibility, BorderLayout.EAST)
        visibility.addActionListener {
            hidden = !hidden
            transcript.text = if (hidden) "••••••••••••••••" else savedText
            visibility.text = if (hidden) "Show" else "Hide"
        }
    }

    private var savedText = ""

    fun append(text: String) {
        if (text.isEmpty()) return
        savedText += text
        if (!hidden) {
            transcript.append(text)
            transcript.caretPosition = transcript.document.length
        }
    }

    fun clear() {
        savedText = ""
        transcript.text = ""
    }
}
