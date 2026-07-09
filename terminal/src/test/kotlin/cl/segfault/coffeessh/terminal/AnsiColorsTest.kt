package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class AnsiColorsTest {

    @Test
    fun base16MatchesClassicXtermBlackAndWhite() {
        assertEquals(0xFF000000.toInt(), AnsiColors.indexed(0))
        assertEquals(0xFFFFFFFF.toInt(), AnsiColors.indexed(15))
    }

    @Test
    fun colorCubeCornersAreBlackAndWhite() {
        assertEquals(0xFF000000.toInt(), AnsiColors.indexed(16)) // cube origin (0,0,0)
        assertEquals(0xFFFFFFFF.toInt(), AnsiColors.indexed(231)) // cube far corner (255,255,255)
    }

    @Test
    fun grayscaleRampEndsNearBlackAndNearWhite() {
        assertEquals(0xFF080808.toInt(), AnsiColors.indexed(232))
        assertEquals(0xFFEEEEEE.toInt(), AnsiColors.indexed(255))
    }

    @Test
    fun resolveHonorsDefaultIndexedAndRgb() {
        assertEquals(0x123456, AnsiColors.resolve(TermColor.Rgb(0x12, 0x34, 0x56), default = 0) and 0xFFFFFF)
        assertEquals(AnsiColors.indexed(200), AnsiColors.resolve(TermColor.Indexed(200), default = 0))
        assertEquals(0xABCDEF, AnsiColors.resolve(TermColor.Default, default = 0xABCDEF))
    }
}
