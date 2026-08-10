package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalColorSchemeTest {
    @Test
    fun allBuiltInSchemesHaveCompletePalettes() {
        TerminalColorScheme.entries.forEach { scheme ->
            assertEquals(16, scheme.ansi16.size)
            assertTrue(scheme.defaultForeground != scheme.defaultBackground)
        }
    }

    @Test
    fun unknownIdsFallBackToCoffee() {
        assertEquals(TerminalColorScheme.COFFEE, TerminalColorScheme.fromId("missing"))
    }

    @Test
    fun schemesReplaceOnlyTheIndexedAnsiPalette() {
        val scheme = TerminalColorScheme.DRACULA
        assertEquals(scheme.ansi16[1], AnsiColors.resolve(TermColor.Indexed(1), 0, scheme.ansi16))
        assertEquals(0x123456, AnsiColors.resolve(TermColor.Default, 0x123456, scheme.ansi16))
        assertEquals(AnsiColors.indexed(200), AnsiColors.resolve(TermColor.Indexed(200), 0, scheme.ansi16))
    }
}
