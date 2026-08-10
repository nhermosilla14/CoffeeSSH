package cl.segfault.coffeessh.terminal

/**
 * Maps [TermColor] to 0xAARRGGBB Int colors (Android's `Color`/`Paint.setColor` format,
 * kept dependency-free here so the mapping is unit-testable on the plain JVM). Palette
 * matches the classic xterm defaults: 16 base colors, a 6x6x6 color cube for indices
 * 16..231, and a 24-step grayscale ramp for 232..255.
 */
object AnsiColors {

    private val BASE16 = intArrayOf(
        argb(0, 0, 0), argb(205, 0, 0), argb(0, 205, 0), argb(205, 205, 0),
        argb(0, 0, 238), argb(205, 0, 205), argb(0, 205, 205), argb(229, 229, 229),
        argb(127, 127, 127), argb(255, 0, 0), argb(0, 255, 0), argb(255, 255, 0),
        argb(92, 92, 255), argb(255, 0, 255), argb(0, 255, 255), argb(255, 255, 255),
    )

    private val CUBE_STEPS = intArrayOf(0, 95, 135, 175, 215, 255)

    /** One of the 256 palette entries. */
    fun indexed(index: Int, palette: IntArray = BASE16): Int {
        val i = index.coerceIn(0, 255)
        return when {
            i < 16 -> palette.getOrElse(i) { BASE16[i] }
            i < 232 -> {
                val n = i - 16
                argb(CUBE_STEPS[n / 36], CUBE_STEPS[(n / 6) % 6], CUBE_STEPS[n % 6])
            }
            else -> {
                val gray = 8 + (i - 232) * 10
                argb(gray, gray, gray)
            }
        }
    }

    /** Resolves a [TermColor], substituting [default] for [TermColor.Default]. */
    fun resolve(color: TermColor, default: Int, palette: IntArray = BASE16): Int = when (color) {
        is TermColor.Default -> default
        is TermColor.Indexed -> indexed(color.index, palette)
        is TermColor.Rgb -> argb(color.r, color.g, color.b)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
