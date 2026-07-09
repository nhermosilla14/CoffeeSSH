package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalModesTest {

    @Test
    fun cursorVisibilityToggle() {
        val t = Terminal(rows = 2, cols = 10)
        assertTrue(t.cursorVisible)
        t.write("\u001b[?25l")
        assertFalse(t.cursorVisible)
        t.write("\u001b[?25h")
        assertTrue(t.cursorVisible)
    }

    @Test
    fun bracketedPasteToggle() {
        val t = Terminal(rows = 2, cols = 10)
        assertFalse(t.modes.bracketedPaste)
        t.write("\u001b[?2004h")
        assertTrue(t.modes.bracketedPaste)
        t.write("\u001b[?2004l")
        assertFalse(t.modes.bracketedPaste)
    }

    @Test
    fun applicationCursorKeysTogglesKeyEncoding() {
        val t = Terminal(rows = 2, cols = 10)
        assertEquals("\u001b[A", KeyEncoder.encode(TerminalKey.ARROW_UP, t).decodeToString())
        t.write("\u001b[?1h")
        assertEquals("\u001bOA", KeyEncoder.encode(TerminalKey.ARROW_UP, t).decodeToString())
    }

    @Test
    fun altScreenModeFlagTracksActiveBuffer() {
        val t = Terminal(rows = 2, cols = 10)
        assertFalse(t.altScreenActive)
        t.write("\u001b[?1049h")
        assertTrue(t.altScreenActive)
        t.write("\u001b[?1049l")
        assertFalse(t.altScreenActive)
    }

    @Test
    fun insertModeToggle() {
        val t = Terminal(rows = 2, cols = 10)
        assertFalse(t.modes.insertMode)
        t.write("\u001b[4h")
        assertTrue(t.modes.insertMode)
        t.write("\u001b[4l")
        assertFalse(t.modes.insertMode)
    }

    @Test
    fun oscSetsWindowTitle() {
        val t = Terminal(rows = 2, cols = 10)
        var seen: String? = null
        t.onTitleChange = { seen = it }
        t.write("\u001b]2;my session\u0007")
        assertEquals("my session", t.title)
        assertEquals("my session", seen)
    }

    @Test
    fun oscTerminatedByStringTerminatorAlsoWorks() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b]0;another title\u001b\\")
        assertEquals("another title", t.title)
        // The trailing ST's backslash must not leak into the grid as a printed character.
        assertEquals("", t.rowTrimmed(0))
    }

    @Test
    fun deviceStatusReportRepliesWithCursorPosition() {
        val t = Terminal(rows = 10, cols = 10)
        var reply: String? = null
        t.onResponse = { reply = it.decodeToString() }
        t.write("\u001b[5;9H")
        t.write("\u001b[6n")
        assertEquals("\u001b[5;9R", reply)
    }

    @Test
    fun resizePreservesTopLeftContentAndClampsCursor() {
        val t = Terminal(rows = 5, cols = 5)
        t.write("Hello")
        t.write("\u001b[5;5H") // bottom-right corner
        t.resize(3, 3)
        assertEquals("Hel", t.rowTrimmed(0))
        assertEquals(2, t.cursorRow)
        assertEquals(2, t.cursorCol)
    }
}
