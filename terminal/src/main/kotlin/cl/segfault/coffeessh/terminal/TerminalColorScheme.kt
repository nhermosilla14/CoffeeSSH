package cl.segfault.coffeessh.terminal

/** Built-in terminal palettes. Colors are Android-style opaque ARGB values. */
enum class TerminalColorScheme(
    val id: String,
    val defaultForeground: Int,
    val defaultBackground: Int,
    val ansi16: IntArray,
) {
    COFFEE(
        id = "coffee",
        defaultForeground = terminalRgb(0xE8, 0xE0, 0xD6),
        defaultBackground = terminalRgb(0x1A, 0x12, 0x0C),
        ansi16 = terminalPalette(
            0x201A17, 0xD46A4C, 0x9BBF7A, 0xD5B26E, 0x8FB7D8, 0xC49ACB, 0x77C0B4, 0xE8E0D6,
            0x76685D, 0xF07F5C, 0xB1D68A, 0xE9CB82, 0xA8D0F0, 0xDBB1E2, 0x91D8C9, 0xFFF8F0,
        ),
    ),
    SOLARIZED(
        id = "solarized",
        defaultForeground = terminalRgb(0x83, 0x94, 0x96),
        defaultBackground = terminalRgb(0x00, 0x2B, 0x36),
        ansi16 = terminalPalette(
            0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
            0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
        ),
    ),
    DRACULA(
        id = "dracula",
        defaultForeground = terminalRgb(0xF8, 0xF8, 0xF2),
        defaultBackground = terminalRgb(0x28, 0x2A, 0x36),
        ansi16 = terminalPalette(
            0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
            0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF,
        ),
    ),
    NORD(
        id = "nord",
        defaultForeground = terminalRgb(0xD8, 0xDE, 0xE9),
        defaultBackground = terminalRgb(0x2E, 0x34, 0x40),
        ansi16 = terminalPalette(
            0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
            0x4C566A, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4,
        ),
    ),
    MONOKAI(
        id = "monokai",
        defaultForeground = terminalRgb(0xF8, 0xF8, 0xF2),
        defaultBackground = terminalRgb(0x27, 0x28, 0x22),
        ansi16 = terminalPalette(
            0x272822, 0xF92672, 0xA6E22E, 0xE6DB74, 0x66D9EF, 0xAE81FF, 0xA1EFE4, 0xF8F8F2,
            0x75715E, 0xF92672, 0xA6E22E, 0xE6DB74, 0x66D9EF, 0xAE81FF, 0xA1EFE4, 0xF9F8F5,
        ),
    );

    companion object {
        fun fromId(id: String): TerminalColorScheme = entries.firstOrNull { it.id == id } ?: COFFEE

    }
}

private fun terminalRgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r shl 16) or (g shl 8) or b

private fun terminalPalette(vararg colors: Int): IntArray =
    colors.map { terminalRgb(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }.toIntArray()
