package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalPrintingTest {

    @Test
    fun printsPlainAsciiAndAdvancesCursor() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("Hello")
        assertEquals("Hello", t.rowTrimmed(0))
        assertEquals(0, t.cursorRow)
        assertEquals(5, t.cursorCol)
    }

    @Test
    fun carriageReturnAndLineFeedMoveIndependently() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("AB\r\nCD")
        assertEquals("AB", t.rowTrimmed(0))
        assertEquals("CD", t.rowTrimmed(1))
        assertEquals(1, t.cursorRow)
        assertEquals(2, t.cursorCol)
    }

    @Test
    fun bareLineFeedDoesNotReturnCarriage() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("AB\nCD")
        // \n alone only moves down; CD prints starting at column 2 (no CR).
        assertEquals("AB", t.rowTrimmed(0))
        assertEquals(1, t.cursorRow)
        assertEquals(4, t.cursorCol)
    }

    @Test
    fun backspaceMovesCursorLeftWithoutErasing() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("AB\bC")
        assertEquals("AC", t.rowTrimmed(0))
    }

    @Test
    fun tabAdvancesToNextEightColumnStop() {
        val t = Terminal(rows = 3, cols = 20)
        t.write("A\tB")
        assertEquals('A'.code, t.cellAt(0, 0).codePoint)
        assertEquals('B'.code, t.cellAt(0, 8).codePoint)
        assertEquals(9, t.cursorCol)
    }

    @Test
    fun autowrapDefersToNextPrintableCharacter() {
        val t = Terminal(rows = 3, cols = 5)
        t.write("Hello") // exactly fills the line
        assertEquals(0, t.cursorRow)
        assertEquals(4, t.cursorCol) // clamped at last column, wrap pending internally
        assertEquals("Hello", t.rowTrimmed(0))

        t.write("!")
        assertEquals(1, t.cursorRow) // wrap consumed on the next print
        assertEquals(1, t.cursorCol)
        assertEquals("!", t.rowTrimmed(1))
    }

    @Test
    fun autowrapDisabledOverwritesLastColumn() {
        val t = Terminal(rows = 3, cols = 5)
        t.write("\u001b[?7l") // DECAWM off
        t.write("Hello!")
        assertEquals(0, t.cursorRow)
        assertEquals(4, t.cursorCol)
        assertEquals("Hell!", t.rowTrimmed(0)) // '!' overwrote 'o' at the last column
    }

    @Test
    fun insertModeShiftsExistingCharactersRight() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("ACD")
        t.write("\u001b[4h") // IRM on
        t.write("\u001b[2G") // CUP column 2
        t.write("B")
        assertEquals("ABCD", t.rowTrimmed(0))
    }

    @Test
    fun combiningMarksAreDroppedNotMergedOrPrinted() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("e\u0301f") // e + combining acute + f
        assertEquals("ef", t.rowTrimmed(0))
        assertEquals(2, t.cursorCol)
    }

    @Test
    fun wideCharacterOccupiesTwoColumnsWithContinuationCell() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("\u4e2d\u6587") // 2 CJK ideographs, width 2 each
        assertEquals(2, t.cellAt(0, 0).width)
        assertEquals(0, t.cellAt(0, 1).width) // continuation
        assertEquals(2, t.cellAt(0, 2).width)
        assertEquals(4, t.cursorCol)
    }
}
