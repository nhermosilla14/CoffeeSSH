package cl.segfault.coffeessh.ui.terminal

import cl.segfault.coffeessh.terminal.Terminal

/**
 * A tiny local pseudo-shell used to exercise the terminal rendering + input pipeline
 * before SSH exists (M3). Not a real shell - just enough local-echo line editing and a
 * handful of builtin commands to prove printing/wrapping/scrolling/colors/alt-screen/
 * wide-chars all work correctly end to end on a real device.
 *
 * Input is decoded as a whole UTF-8 chunk per call and then walked char-by-char; unlike
 * [Terminal] itself (which streams raw bytes through a proper incremental UTF-8 decoder),
 * this demo harness doesn't bother pairing surrogates split across separate IME commits -
 * an acceptable gap for a throwaway dev tool that never ships user-facing.
 */
class DemoShell(private val terminal: Terminal) {

    private val lineBuffer = StringBuilder()
    private var inAltScreenDemo = false

    fun start() {
        terminal.write(BANNER)
        prompt()
    }

    fun onInput(bytes: ByteArray) {
        for (ch in bytes.decodeToString()) {
            when (ch.code) {
                0x0D, 0x0A -> {
                    if (inAltScreenDemo) {
                        inAltScreenDemo = false
                        terminal.write("\u001b[?1049l")
                        prompt()
                    } else {
                        terminal.write("\r\n")
                        runCommand(lineBuffer.toString().trim())
                    }
                    lineBuffer.setLength(0)
                }
                0x7F, 0x08 -> {
                    if (!inAltScreenDemo && lineBuffer.isNotEmpty()) {
                        lineBuffer.deleteCharAt(lineBuffer.length - 1)
                        terminal.write("\b \b")
                    }
                }
                0x03 -> {
                    lineBuffer.setLength(0)
                    inAltScreenDemo = false
                    terminal.write("^C\r\n")
                    prompt()
                }
                else -> {
                    if (!inAltScreenDemo && !ch.isISOControl()) {
                        lineBuffer.append(ch)
                        terminal.write(ch.toString())
                    }
                }
            }
        }
    }

    private fun prompt() = terminal.write(PROMPT)

    private fun runCommand(line: String) {
        if (line.isEmpty()) {
            prompt()
            return
        }
        val parts = line.split(" ", limit = 2)
        when (parts[0]) {
            "help" -> { terminal.write(HELP_TEXT); prompt() }
            "colors" -> { printColorChart(); prompt() }
            "boxes" -> { printBoxes(); prompt() }
            "wide" -> { terminal.write("中文字符 日本語 한국어\r\n"); prompt() }
            "wrap" -> { terminal.write("x".repeat(terminal.cols * 2) + "\r\n"); prompt() }
            "clear" -> { terminal.write("\u001b[2J\u001b[H"); prompt() }
            "altscreen" -> demoAltScreen()
            "echo" -> { terminal.write((parts.getOrNull(1) ?: "") + "\r\n"); prompt() }
            else -> { terminal.write("coffeessh-demo: command not found: ${parts[0]}\r\n"); prompt() }
        }
    }

    private fun printColorChart() {
        val sb = StringBuilder("Standard 16 colors:\r\n")
        for (i in 0..15) {
            sb.append("\u001b[48;5;${i}m  \u001b[0m")
            if (i == 7) sb.append("\r\n")
        }
        sb.append("\u001b[0m\r\n\r\n256-color cube (first 6 rows):\r\n")
        for (i in 16..51) sb.append("\u001b[48;5;${i}m  \u001b[0m")
        sb.append("\u001b[0m\r\n")
        terminal.write(sb.toString())
    }

    private fun printBoxes() {
        terminal.write("Unicode box-drawing:\r\n┌────────────┐\r\n│ Unicode box │\r\n└────────────┘\r\n\r\n")
        terminal.write("DEC Special Graphics charset (same glyphs, different code path):\r\n")
        terminal.write("\u001b)0") // designate G1 as special graphics (doesn't invoke it yet)
        terminal.write("\u000elqqqqqqqqqqqqqk\u000f\r\n") // SO border SI
        terminal.write("\u000ex\u000f DEC charset \u000ex\u000f\r\n") // SO-x-SI label SO-x-SI
        terminal.write("\u000emqqqqqqqqqqqqqj\u000f\r\n")
    }

    private fun demoAltScreen() {
        inAltScreenDemo = true
        terminal.write("\u001b[?1049h\u001b[2J\u001b[H")
        terminal.write("This is the alternate screen buffer, like vim/less/tmux use.\r\n")
        terminal.write("Your previous screen content is preserved underneath.\r\n\r\n")
        terminal.write("Press Enter to return.")
    }

    private companion object {
        const val PROMPT = "coffeessh-demo$ "
        val BANNER = "CoffeeSSH terminal engine demo - no SSH session yet (milestone M2)\r\n" +
            "Type 'help' for available commands.\r\n\r\n"
        val HELP_TEXT = "Available commands:\r\n" +
            "  help       show this text\r\n" +
            "  colors     print 16-color + 256-color palette sample\r\n" +
            "  boxes      print box-drawing characters (Unicode + DEC charset)\r\n" +
            "  wide       print CJK wide characters\r\n" +
            "  wrap       print a line longer than the terminal width\r\n" +
            "  altscreen  switch to the alternate screen buffer (Enter returns)\r\n" +
            "  echo TEXT  print TEXT back\r\n" +
            "  clear      clear the screen\r\n"
    }
}
