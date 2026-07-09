package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalEraseTest {

    private fun filled(rows: Int = 3, cols: Int = 5): Terminal {
        val t = Terminal(rows, cols)
        for (r in 0 until rows) {
            t.write("\u001b[${r + 1};1H")
            t.write("X".repeat(cols))
        }
        return t
    }

    @Test
    fun eraseToEndOfLine() {
        val t = filled()
        t.write("\u001b[2;3H\u001b[0K")
        assertEquals("XX", t.rowTrimmed(1))
    }

    @Test
    fun eraseToStartOfLine() {
        val t = filled()
        t.write("\u001b[2;3H\u001b[1K")
        // columns 0..2 cleared (inclusive of cursor), columns 3..4 remain 'X'
        assertEquals("   XX", t.rowText(1))
    }

    @Test
    fun eraseWholeLine() {
        val t = filled()
        t.write("\u001b[2;3H\u001b[2K")
        assertEquals("", t.rowTrimmed(1))
    }

    @Test
    fun eraseDisplayBelowCursor() {
        val t = filled()
        t.write("\u001b[2;3H\u001b[0J")
        assertEquals("XXXXX", t.rowTrimmed(0))
        assertEquals("XX", t.rowTrimmed(1))
        assertEquals("", t.rowTrimmed(2))
    }

    @Test
    fun eraseDisplayAboveCursor() {
        val t = filled()
        t.write("\u001b[2;3H\u001b[1J")
        assertEquals("", t.rowTrimmed(0))
        assertEquals("   XX", t.rowText(1))
        assertEquals("XXXXX", t.rowTrimmed(2))
    }

    @Test
    fun eraseWholeDisplay() {
        val t = filled()
        t.write("\u001b[2J")
        for (r in 0 until 3) assertEquals("", t.rowTrimmed(r))
    }

    @Test
    fun deleteAndInsertCharacters() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("ABCDE")
        t.write("\u001b[2G\u001b[2P") // delete 2 chars starting at column 2 ('B','C' removed)
        assertEquals("ADE", t.rowTrimmed(0))

        t.write("\u001b[1G\u001b[2@") // insert 2 blanks at column 1
        assertEquals("  ADE", t.rowTrimmed(0))
    }

    @Test
    fun eraseCharsClearsWithoutShifting() {
        val t = Terminal(rows = 3, cols = 10)
        t.write("ABCDE")
        t.write("\u001b[2G\u001b[2X") // erase 2 chars from column 2
        assertEquals("A  DE", t.rowTrimmed(0))
    }
}
