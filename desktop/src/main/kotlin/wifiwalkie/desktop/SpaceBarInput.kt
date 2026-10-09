package wifiwalkie.desktop

import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Timer

/**
 * Reports when the space bar goes down and up while [window] has focus.
 * [onChange] is called on the Swing thread.
 *
 * Holding a key makes the OS repeat it. On Windows and macOS that repeats only
 * the "pressed" event, but on Linux each repeat is a released-then-pressed
 * pair. So a release only counts if no press follows within [REPEAT_GAP_MS].
 */
class SpaceBarInput(window: Window, private val onChange: (Boolean) -> Unit) {
    private var down = false
    private val releaseTimer = Timer(REPEAT_GAP_MS) { setDown(false) }.apply { isRepeats = false }

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
            if (event.keyCode != KeyEvent.VK_SPACE) return@addKeyEventDispatcher false
            when (event.id) {
                KeyEvent.KEY_PRESSED -> {
                    releaseTimer.stop()
                    setDown(true)
                }
                KeyEvent.KEY_RELEASED -> releaseTimer.restart()
            }
            true
        }
        // If the window loses focus while space is held, we'd never see the release.
        window.addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(event: WindowEvent) {
                releaseTimer.stop()
                setDown(false)
            }
        })
    }

    private fun setDown(value: Boolean) {
        if (value == down) return
        down = value
        onChange(value)
    }

    private companion object {
        const val REPEAT_GAP_MS = 15
    }
}
