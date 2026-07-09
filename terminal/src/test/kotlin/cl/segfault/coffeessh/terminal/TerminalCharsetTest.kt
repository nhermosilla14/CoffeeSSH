package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalCharsetTest {

    @Test
    fun decSpecialGraphicsTranslatesBoxDrawingChars() {
        val t = Terminal(rows = 2, cols = 10)
        // ESC ) 0 designates G1 as special graphics; SO (0x0E) invokes it; 'q' -> '─'.
        t.write("\u001b)0")
        t.write("\u000e") // SO
        t.write("qqq")
        t.write("\u000f") // SI back to ASCII (G0)
        t.write("q")
        assertEquals("───q", t.rowTrimmed(0))
    }

    @Test
    fun boxCornersAndTeesTranslateCorrectly() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b)0\u000e")
        t.write("lkmjtuvw") // corners + tees
        assertEquals("┌┐└┘├┤┴┬", t.rowTrimmed(0))
    }
}
