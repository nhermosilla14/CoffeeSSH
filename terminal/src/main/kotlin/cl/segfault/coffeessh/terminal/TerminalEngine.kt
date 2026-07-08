package cl.segfault.coffeessh.terminal

/**
 * Placeholder for the CoffeeSSH terminal emulator engine (milestone M2).
 *
 * This module must stay free of Android dependencies so the whole engine can be
 * unit-tested on the JVM.
 */
object TerminalEngine {
    /** Terminal type reported to remote hosts via the SSH PTY request. */
    const val TERM_TYPE: String = "xterm-256color"
}
