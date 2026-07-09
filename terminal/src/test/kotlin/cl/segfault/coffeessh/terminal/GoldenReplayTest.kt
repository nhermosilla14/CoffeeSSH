package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replays raw byte captures from real programs (see `terminal/golden-fixtures/README.md`
 * for how they were generated) through our [Terminal] and compares the resulting grid,
 * row by row, against an independent oracle rendering computed offline by `pyte`
 * (a separately-implemented Python VT100/xterm emulator). Matching a second, unrelated
 * implementation is a much stronger correctness signal than hand-written expectations.
 */
class GoldenReplayTest {

    private data class Fixture(val cols: Int, val rows: Int, val raw: ByteArray, val expectedLines: List<String>)

    private fun loadFixture(name: String): Fixture {
        val classLoader = Thread.currentThread().contextClassLoader
        val raw = classLoader.getResourceAsStream("golden/$name.bin")!!.use { it.readBytes() }
        val meta = classLoader.getResourceAsStream("golden/$name.meta.txt")!!
            .use { it.readBytes().decodeToString() }
            .lineSequence()
            .filter { it.isNotBlank() }
            .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        val expected = classLoader.getResourceAsStream("golden/$name.expected.txt")!!
            .use { it.readBytes().decodeToString() }
            .split("\n")
            .let { it.subList(0, it.size - 1) } // drop the trailing blank from the final newline
        return Fixture(
            cols = meta.getValue("cols").toInt(),
            rows = meta.getValue("rows").toInt(),
            raw = raw,
            expectedLines = expected,
        )
    }

    private fun replay(name: String) {
        val fixture = loadFixture(name)
        val terminal = Terminal(rows = fixture.rows, cols = fixture.cols)
        terminal.write(fixture.raw)

        assertEquals(fixture.expectedLines.size, fixture.rows, "fixture sanity: $name row count")
        for (row in 0 until fixture.rows) {
            assertEquals(
                fixture.expectedLines[row],
                terminal.rowText(row),
                "row $row mismatch in fixture '$name'",
            )
        }
    }

    @Test
    fun plainShellWrappingAndScrolling() = replay("plain_wrap_scroll")

    @Test
    fun vimAltScreenAndStatusLine() = replay("vim_basic")

    @Test
    fun tmuxStatusBarAndColorOutput() = replay("tmux_basic")
}
