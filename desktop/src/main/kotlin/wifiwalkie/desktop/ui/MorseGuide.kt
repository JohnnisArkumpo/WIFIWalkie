package wifiwalkie.desktop.ui

import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

/** Compact reference table for letters and digits in International Morse. */
class MorseGuide : JPanel(BorderLayout(0, 6)) {
    init {
        border = BorderFactory.createTitledBorder("Morse guide")
        val title = JLabel("Dots are short •   Dashes are long —")
        title.font = title.font.deriveFont(Font.PLAIN, 12f)
        add(title, BorderLayout.NORTH)
        val rows = arrayOf(
            arrayOf("A  .-", "B  -...", "C  -.-.", "D  -..", "E  .", "F  ..-."),
            arrayOf("G  --.", "H  ....", "I  ..", "J  .---", "K  -.-", "L  .-.."),
            arrayOf("M  --", "N  -.", "O  ---", "P  .--.", "Q  --.-", "R  .-."),
            arrayOf("S  ...", "T  -", "U  ..-", "V  ...-", "W  .--", "X  -..-"),
            arrayOf("Y  -.--", "Z  --..", "0  -----", "1  .----", "2  ..---", "3  ...--"),
            arrayOf("4  ....-", "5  .....", "6  -....", "7  --...", "8  ---..", "9  ----."),
            arrayOf(".  .-.-.-", ",  --..--", "?  ..--..", "/  -..-.", "=  -...-", "-  -....-")
        )
        val model = object : DefaultTableModel(rows, arrayOf("", "", "", "", "", "")) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        val table = JTable(model).apply {
            tableHeader = null
            rowHeight = 24
            setShowGrid(false)
            setBackground(background)
        }
        add(JScrollPane(table), BorderLayout.CENTER)
    }
}
