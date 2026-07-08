package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalEngineTest {

    @Test
    fun `module wiring works`() {
        assertEquals("xterm-256color", TerminalEngine.TERM_TYPE)
    }
}
