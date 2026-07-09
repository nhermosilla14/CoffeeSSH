package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyEncoderTest {

    @Test
    fun arrowKeysUseCsiByDefault() {
        val t = Terminal(rows = 5, cols = 5)
        assertEquals("\u001b[A", KeyEncoder.encode(TerminalKey.ARROW_UP, t).decodeToString())
        assertEquals("\u001b[B", KeyEncoder.encode(TerminalKey.ARROW_DOWN, t).decodeToString())
        assertEquals("\u001b[C", KeyEncoder.encode(TerminalKey.ARROW_RIGHT, t).decodeToString())
        assertEquals("\u001b[D", KeyEncoder.encode(TerminalKey.ARROW_LEFT, t).decodeToString())
    }

    @Test
    fun arrowKeysUseSs3InApplicationMode() {
        val t = Terminal(rows = 5, cols = 5)
        t.write("\u001b[?1h")
        assertEquals("\u001bOA", KeyEncoder.encode(TerminalKey.ARROW_UP, t).decodeToString())
    }

    @Test
    fun pageAndEditingKeysUseTildeForm() {
        val t = Terminal(rows = 5, cols = 5)
        assertEquals("\u001b[5~", KeyEncoder.encode(TerminalKey.PAGE_UP, t).decodeToString())
        assertEquals("\u001b[6~", KeyEncoder.encode(TerminalKey.PAGE_DOWN, t).decodeToString())
        assertEquals("\u001b[3~", KeyEncoder.encode(TerminalKey.DELETE, t).decodeToString())
    }

    @Test
    fun ctrlLetterProducesC0Control() {
        assertEquals(0x03, KeyEncoder.encodeCtrl('c')[0].toInt())
        assertEquals(0x01, KeyEncoder.encodeCtrl('A')[0].toInt())
    }

    @Test
    fun basicKeysMapToTheirControlBytes() {
        val t = Terminal(rows = 5, cols = 5)
        assertEquals(0x0D, KeyEncoder.encode(TerminalKey.ENTER, t)[0].toInt())
        assertEquals(0x7F, KeyEncoder.encode(TerminalKey.BACKSPACE, t)[0].toInt())
        assertEquals(0x1B, KeyEncoder.encode(TerminalKey.ESCAPE, t)[0].toInt())
    }
}
