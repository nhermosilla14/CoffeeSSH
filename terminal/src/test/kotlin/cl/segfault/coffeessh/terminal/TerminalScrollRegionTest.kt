package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalScrollRegionTest {

    @Test
    fun scrollingFullScreenPushesToScrollback() {
        val t = Terminal(rows = 3, cols = 5)
        t.write("A\r\nB\r\nC\r\nD") // 4 lines through a 3-row screen
        assertEquals("B", t.rowTrimmed(0))
        assertEquals("C", t.rowTrimmed(1))
        assertEquals("D", t.rowTrimmed(2))
        assertEquals(1, t.scrollbackSize)
        assertEquals('A'.code, t.scrollbackLine(0)[0].codePoint)
    }

    @Test
    fun partialScrollRegionDoesNotTouchScrollback() {
        val t = Terminal(rows = 5, cols = 5)
        t.write("\u001b[2;4r") // region rows 2..4 (1-based) -> 1..3 (0-based); top != 0
        // Fill full screen first (region is applied only to scrolling, not to this initial paint)
        t.write("\u001b[?6l") // ensure origin mode off for absolute addressing while painting
        for (r in 0 until 5) {
            t.write("\u001b[${r + 1};1H${('A' + r)}")
        }
        t.write("\u001b[4;1H") // bottom of the scroll region
        t.write("\n\n\n\n") // scroll the region several times, well past its size
        assertEquals(0, t.scrollbackSize) // region top != 0, so nothing should reach history
        assertEquals("A", t.rowTrimmed(0)) // row above the region: untouched
        assertEquals("E", t.rowTrimmed(4)) // row below the region: untouched
    }

    @Test
    fun reverseIndexScrollsDownAtRegionTop() {
        val t = Terminal(rows = 4, cols = 5)
        t.write("\u001b[1;1HAAAAA\u001b[2;1HBBBBB\u001b[3;1HCCCCC\u001b[4;1HDDDDD")
        t.write("\u001b[1;1H") // top-left
        t.write("\u001bM") // RI: at scroll top, scrolls the whole screen down
        assertEquals("", t.rowTrimmed(0))
        assertEquals("AAAAA", t.rowTrimmed(1))
        assertEquals("BBBBB", t.rowTrimmed(2))
        assertEquals("CCCCC", t.rowTrimmed(3)) // DDDDD fell off the bottom
        assertEquals(0, t.scrollbackSize) // reverse-scrolled lines never go to history
    }

    @Test
    fun insertAndDeleteLinesRespectScrollRegion() {
        val t = Terminal(rows = 5, cols = 5)
        for (r in 0 until 5) t.write("\u001b[${r + 1};1H${('A' + r)}")
        t.write("\u001b[2;4r") // region rows 2..4 (1-based) -> 1..3 (0-based)
        t.write("\u001b[2;1H") // top of region
        t.write("\u001b[1M") // DL 1: delete row index1, pulling C up, blank at region bottom (index3)
        assertEquals("A", t.rowTrimmed(0)) // untouched (outside region)
        assertEquals("C", t.rowTrimmed(1))
        assertEquals("D", t.rowTrimmed(2))
        assertEquals("", t.rowTrimmed(3)) // region bottom now blank
        assertEquals("E", t.rowTrimmed(4)) // untouched (outside region)
    }

    @Test
    fun scrollUpAndDownCsiSequences() {
        val t = Terminal(rows = 3, cols = 5)
        t.write("AAAAA\r\nBBBBB\r\nCCCCC")
        t.write("\u001b[2S") // SU 2: scroll whole screen up by 2
        assertEquals("CCCCC", t.rowTrimmed(0))
        assertEquals("", t.rowTrimmed(1))
        assertEquals("", t.rowTrimmed(2))
        assertEquals(2, t.scrollbackSize)
    }

    @Test
    fun alternateScreenHasNoScrollbackAndIsDiscardedOnExit() {
        val t = Terminal(rows = 3, cols = 5)
        t.write("Main1\r\nMain2")
        t.write("\u001b[?1049h") // enter alt screen
        assertEquals("", t.rowTrimmed(0)) // cleared
        t.write("Alt1\r\nAlt2\r\nAlt3\r\nAlt4") // force scrolling within alt screen
        assertEquals(0, t.scrollbackSize) // alt screen never contributes to scrollback
        t.write("\u001b[?1049l") // exit alt screen: main content + cursor restored
        assertEquals("Main1", t.rowTrimmed(0))
        assertEquals("Main2", t.rowTrimmed(1))
    }
}
