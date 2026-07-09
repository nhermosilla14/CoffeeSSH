package cl.segfault.coffeessh.terminal

/** Special (non-printing) keys the terminal knows how to encode as byte sequences. */
enum class TerminalKey {
    ARROW_UP, ARROW_DOWN, ARROW_RIGHT, ARROW_LEFT,
    HOME, END, INSERT, DELETE, PAGE_UP, PAGE_DOWN,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    ENTER, TAB, BACKSPACE, ESCAPE,
}

/**
 * Encodes key presses into the byte sequences a remote shell expects, honoring
 * [Modes.applicationCursorKeys] (DECCKM) where it changes the encoding (arrow/Home/End keys).
 */
object KeyEncoder {

    fun encode(key: TerminalKey, terminal: Terminal): ByteArray {
        val appCursor = terminal.modes.applicationCursorKeys
        return when (key) {
            TerminalKey.ARROW_UP -> if (appCursor) ESC_O_SEQ('A') else ESC_CSI_SEQ('A')
            TerminalKey.ARROW_DOWN -> if (appCursor) ESC_O_SEQ('B') else ESC_CSI_SEQ('B')
            TerminalKey.ARROW_RIGHT -> if (appCursor) ESC_O_SEQ('C') else ESC_CSI_SEQ('C')
            TerminalKey.ARROW_LEFT -> if (appCursor) ESC_O_SEQ('D') else ESC_CSI_SEQ('D')
            TerminalKey.HOME -> if (appCursor) ESC_O_SEQ('H') else ESC_CSI_SEQ('H')
            TerminalKey.END -> if (appCursor) ESC_O_SEQ('F') else ESC_CSI_SEQ('F')
            TerminalKey.INSERT -> csiTilde(2)
            TerminalKey.DELETE -> csiTilde(3)
            TerminalKey.PAGE_UP -> csiTilde(5)
            TerminalKey.PAGE_DOWN -> csiTilde(6)
            TerminalKey.F1 -> ESC_O_SEQ('P')
            TerminalKey.F2 -> ESC_O_SEQ('Q')
            TerminalKey.F3 -> ESC_O_SEQ('R')
            TerminalKey.F4 -> ESC_O_SEQ('S')
            TerminalKey.F5 -> csiTilde(15)
            TerminalKey.F6 -> csiTilde(17)
            TerminalKey.F7 -> csiTilde(18)
            TerminalKey.F8 -> csiTilde(19)
            TerminalKey.F9 -> csiTilde(20)
            TerminalKey.F10 -> csiTilde(21)
            TerminalKey.F11 -> csiTilde(23)
            TerminalKey.F12 -> csiTilde(24)
            TerminalKey.ENTER -> byteArrayOf(0x0D)
            TerminalKey.TAB -> byteArrayOf(0x09)
            TerminalKey.BACKSPACE -> byteArrayOf(0x7F)
            TerminalKey.ESCAPE -> byteArrayOf(0x1B)
        }
    }

    /** `Ctrl+letter` -> its C0 control code (e.g. Ctrl+C -> 0x03 ETX). */
    fun encodeCtrl(letter: Char): ByteArray {
        val upper = letter.uppercaseChar()
        val code = (upper.code - 'A'.code + 1) and 0x1F
        return byteArrayOf(code.toByte())
    }

    private fun ESC_CSI_SEQ(final: Char): ByteArray = byteArrayOf(0x1B, '['.code.toByte(), final.code.toByte())
    private fun ESC_O_SEQ(final: Char): ByteArray = byteArrayOf(0x1B, 'O'.code.toByte(), final.code.toByte())
    private fun csiTilde(n: Int): ByteArray = "\u001b[$n~".encodeToByteArray()
}
