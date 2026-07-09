package cl.segfault.coffeessh.terminal

/**
 * A terminal color as produced by SGR (Select Graphic Rendition) sequences.
 *
 * [Default] means "inherit the viewer's default foreground/background" (SGR 39/49).
 * [Indexed] is one of the 256 palette entries (SGR 38;5;n / 48;5;n, or the classic 30-37/40-47
 * and 90-97/100-107 8/16-color forms, normalized to 0..255 here).
 * [Rgb] is 24-bit truecolor (SGR 38;2;r;g;b / 48;2;r;g;b).
 */
sealed interface TermColor {
    data object Default : TermColor
    data class Indexed(val index: Int) : TermColor
    data class Rgb(val r: Int, val g: Int, val b: Int) : TermColor
}

/** SGR text attributes attached to a [Cell]. */
data class CellAttrs(
    val fg: TermColor = TermColor.Default,
    val bg: TermColor = TermColor.Default,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val blink: Boolean = false,
    val inverse: Boolean = false,
    val invisible: Boolean = false,
    val strikethrough: Boolean = false,
)

/**
 * A single grid cell. [width] is 1 for normal characters, 2 for the first column of a
 * wide (e.g. CJK) character, and 0 for the "continuation" placeholder that follows it
 * so the grid stays rectangular and index-addressable.
 */
data class Cell(
    val codePoint: Int = ' '.code,
    val attrs: CellAttrs = CellAttrs(),
    val width: Int = 1,
) {
    companion object {
        val BLANK = Cell()
        fun blank(attrs: CellAttrs) = Cell(attrs = attrs)
    }
}
