package cl.segfault.coffeessh.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

private fun scheme(
    dark: Boolean,
    primary: Long,
    onPrimary: Long,
    secondary: Long,
    tertiary: Long,
    background: Long,
    surface: Long,
): ColorScheme = if (dark) {
    darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(primary),
        onPrimary = androidx.compose.ui.graphics.Color(onPrimary),
        secondary = androidx.compose.ui.graphics.Color(secondary),
        tertiary = androidx.compose.ui.graphics.Color(tertiary),
        background = androidx.compose.ui.graphics.Color(background),
        surface = androidx.compose.ui.graphics.Color(surface),
    )
} else {
    lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(primary),
        onPrimary = androidx.compose.ui.graphics.Color(onPrimary),
        secondary = androidx.compose.ui.graphics.Color(secondary),
        tertiary = androidx.compose.ui.graphics.Color(tertiary),
        background = androidx.compose.ui.graphics.Color(background),
        surface = androidx.compose.ui.graphics.Color(surface),
    )
}

internal val OceanLightColors = scheme(false, 0xFF00658A, 0xFFFFFFFF, 0xFF4F616C, 0xFF5B5D92, 0xFFF7FAFC, 0xFFF7FAFC)
internal val OceanDarkColors = scheme(true, 0xFF56C9FF, 0xFF003548, 0xFFB3CAD5, 0xFFC2C3FF, 0xFF101416, 0xFF101416)
internal val ForestLightColors = scheme(false, 0xFF416A3D, 0xFFFFFFFF, 0xFF56644F, 0xFF6B5E35, 0xFFF9FBF3, 0xFFF9FBF3)
internal val ForestDarkColors = scheme(true, 0xFFA6D391, 0xFF12370F, 0xFFC0CCB6, 0xFFD8C58D, 0xFF11140F, 0xFF11140F)
internal val AmberLightColors = scheme(false, 0xFF8B5000, 0xFFFFFFFF, 0xFF705B40, 0xFF725A78, 0xFFFFFBF6, 0xFFFFFBF6)
internal val AmberDarkColors = scheme(true, 0xFFFFB951, 0xFF492900, 0xFFD9C2A4, 0xFFE0BBDD, 0xFF17130D, 0xFF17130D)
internal val VioletLightColors = scheme(false, 0xFF6750A4, 0xFFFFFFFF, 0xFF625B71, 0xFF81537D, 0xFFFFF8FF, 0xFFFFF8FF)
internal val VioletDarkColors = scheme(true, 0xFFD0BCFF, 0xFF381E72, 0xFFCCC2DC, 0xFFEFB8C8, 0xFF151218, 0xFF151218)
