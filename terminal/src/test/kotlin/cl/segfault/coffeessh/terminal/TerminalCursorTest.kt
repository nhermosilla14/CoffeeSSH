package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TerminalCursorTest {

    @Test
    fun cupMovesToOneBasedRowColumn() {
        val t = Terminal(rows = 10, cols = 10)
        t.write("\u001b[3;5H")
        assertEquals(2, t.cursorRow)
        assertEquals(4, t.cursorCol)
    }

    @Test
    fun cupWithNoParamsGoesHome() {
        val t = Terminal(rows = 10, cols = 10)
        t.write("\u001b[5;5H\u001b[H")
        assertEquals(0, t.cursorRow)
        assertEquals(0, t.cursorCol)
    }

    @Test
    fun relativeCursorMovementClampsToScreenBounds() {
        val t = Terminal(rows = 5, cols = 5)
        t.write("\u001b[100A") // CUU past the top
        assertEquals(0, t.cursorRow)
        t.write("\u001b[100B") // CUD past the bottom
        assertEquals(4, t.cursorRow)
        t.write("\u001b[100D")
        assertEquals(0, t.cursorCol)
        t.write("\u001b[100C")
        assertEquals(4, t.cursorCol)
    }

    @Test
    fun cursorNextLineAndPrevLineResetColumn() {
        val t = Terminal(rows = 5, cols = 5)
        t.write("\u001b[3;3H")
        t.write("\u001b[E") // CNL, default 1
        assertEquals(3, t.cursorRow)
        assertEquals(0, t.cursorCol)
        t.write("\u001b[F") // CPL
        assertEquals(2, t.cursorRow)
        assertEquals(0, t.cursorCol)
    }

    @Test
    fun cursorHorizontalAbsoluteIsOneBased() {
        val t = Terminal(rows = 5, cols = 10)
        t.write("\u001b[5G")
        assertEquals(4, t.cursorCol)
    }

    @Test
    fun decscDecrcRoundTripsPositionAndAttributes() {
        val t = Terminal(rows = 10, cols = 10)
        t.write("\u001b[3;3H\u001b[31m") // move + red fg
        t.write("\u001b7") // DECSC
        t.write("\u001b[8;8H\u001b[0m") // move elsewhere, reset attrs
        t.write("\u001b8") // DECRC
        assertEquals(2, t.cursorRow)
        assertEquals(2, t.cursorCol)
        t.write("X")
        assertEquals(TermColor.Indexed(1), t.cellAt(2, 2).attrs.fg)
    }

    @Test
    fun csiSaveRestoreCursorWorksLikeDecsc() {
        val t = Terminal(rows = 10, cols = 10)
        t.write("\u001b[4;4H\u001b[s")
        t.write("\u001b[1;1H\u001b[u")
        assertEquals(3, t.cursorRow)
        assertEquals(3, t.cursorCol)
    }

    @Test
    fun originModeConfinesCursorToScrollRegion() {
        val t = Terminal(rows = 10, cols = 10)
        t.write("\u001b[3;7r") // scroll region rows 3..7 (1-based) -> 2..6 (0-based)
        t.write("\u001b[?6h") // DECOM on
        t.write("\u001b[1;1H") // "home" is now relative to the region
        assertEquals(2, t.cursorRow)
        assertEquals(0, t.cursorCol)

        t.write("\u001b[100B") // CUD should clamp to region bottom, not screen bottom
        assertEquals(6, t.cursorRow)
    }

    @Test
    fun originModeOffAllowsFullScreenMovement() {
        val t = Terminal(rows = 10, cols = 10)
        t.write("\u001b[3;7r")
        t.write("\u001b[1;1H")
        assertFalse(t.modes.originMode)
        assertEquals(0, t.cursorRow)
        t.write("\u001b[100B")
        assertEquals(9, t.cursorRow) // full screen bottom, region ignored
    }
}
