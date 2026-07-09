package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalSgrTest {

    @Test
    fun basicAttributesAndReset() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[1;4;7mX")
        val attrs = t.cellAt(0, 0).attrs
        assertTrue(attrs.bold)
        assertTrue(attrs.underline)
        assertTrue(attrs.inverse)

        t.write("\u001b[0mY")
        val reset = t.cellAt(0, 1).attrs
        assertFalse(reset.bold)
        assertFalse(reset.underline)
        assertFalse(reset.inverse)
    }

    @Test
    fun standardAndBrightIndexedColors() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[31;42mA") // fg red, bg green
        assertEquals(TermColor.Indexed(1), t.cellAt(0, 0).attrs.fg)
        assertEquals(TermColor.Indexed(2), t.cellAt(0, 0).attrs.bg)

        t.write("\u001b[91;102mB") // bright fg red, bright bg green
        assertEquals(TermColor.Indexed(9), t.cellAt(0, 1).attrs.fg)
        assertEquals(TermColor.Indexed(10), t.cellAt(0, 1).attrs.bg)
    }

    @Test
    fun extended256ColorPalette() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[38;5;208mA") // 256-color orange fg
        assertEquals(TermColor.Indexed(208), t.cellAt(0, 0).attrs.fg)

        t.write("\u001b[48;5;21mB") // 256-color blue bg
        assertEquals(TermColor.Indexed(21), t.cellAt(0, 1).attrs.bg)
    }

    @Test
    fun truecolorRgb() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[38;2;12;34;56mA")
        assertEquals(TermColor.Rgb(12, 34, 56), t.cellAt(0, 0).attrs.fg)
    }

    @Test
    fun defaultColorResetsToDefault() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[31mA\u001b[39mB")
        assertEquals(TermColor.Indexed(1), t.cellAt(0, 0).attrs.fg)
        assertEquals(TermColor.Default, t.cellAt(0, 1).attrs.fg)
    }

    @Test
    fun attributesPersistAcrossMultipleCharacters() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[1mBold text")
        for (c in 0 until "Bold text".length) {
            assertTrue(t.cellAt(0, c).attrs.bold, "column $c should still be bold")
        }
    }

    @Test
    fun individualAttributeOffCodesOnlyClearThatAttribute() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[1;3mA\u001b[23mB") // bold+italic, then italic off only
        assertTrue(t.cellAt(0, 1).attrs.bold)
        assertFalse(t.cellAt(0, 1).attrs.italic)
    }

    @Test
    fun multipleSgrParamsInOneSequence() {
        val t = Terminal(rows = 2, cols = 10)
        t.write("\u001b[1;31;44mA")
        val attrs = t.cellAt(0, 0).attrs
        assertTrue(attrs.bold)
        assertEquals(TermColor.Indexed(1), attrs.fg)
        assertEquals(TermColor.Indexed(4), attrs.bg)
    }
}
