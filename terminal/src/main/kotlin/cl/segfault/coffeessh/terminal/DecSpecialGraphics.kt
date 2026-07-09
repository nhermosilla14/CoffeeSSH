package cl.segfault.coffeessh.terminal

/**
 * DEC Special Graphics character set (the classic VT100 "line drawing" set selected via
 * `ESC ) 0` into G1 and invoked with SO/SI). Some ncurses configurations still emit this
 * instead of UTF-8 box-drawing glyphs directly. Maps ASCII 0x5F..0x7E to their glyphs;
 * anything outside that range or not overridden here passes through unchanged.
 */
internal object DecSpecialGraphics {
    private val table: Map<Int, Int> = mapOf(
        '`'.code to '◆'.code,
        'a'.code to '▒'.code,
        'b'.code to '\u2409'.code, // HT symbol
        'c'.code to '\u240c'.code, // FF symbol
        'd'.code to '\u240d'.code, // CR symbol
        'e'.code to '\u240a'.code, // LF symbol
        'f'.code to '°'.code,
        'g'.code to '±'.code,
        'h'.code to '\u2424'.code, // NL symbol
        'i'.code to '\u240b'.code, // VT symbol
        'j'.code to '┘'.code,
        'k'.code to '┐'.code,
        'l'.code to '┌'.code,
        'm'.code to '└'.code,
        'n'.code to '┼'.code,
        'o'.code to '⎺'.code,
        'p'.code to '⎻'.code,
        'q'.code to '─'.code,
        'r'.code to '⎼'.code,
        's'.code to '⎽'.code,
        't'.code to '├'.code,
        'u'.code to '┤'.code,
        'v'.code to '┴'.code,
        'w'.code to '┬'.code,
        'x'.code to '│'.code,
        'y'.code to '≤'.code,
        'z'.code to '≥'.code,
        '{'.code to 'π'.code,
        '|'.code to '≠'.code,
        '}'.code to '£'.code,
        '~'.code to '·'.code,
    )

    fun translate(codePoint: Int): Int = table[codePoint] ?: codePoint
}
