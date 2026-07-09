package cl.segfault.coffeessh.ui.terminal

import androidx.annotation.FontRes
import cl.segfault.coffeessh.R

/** Bundled monospace fonts the terminal can render with (see PLAN.md section 6). */
enum class TerminalFont(@FontRes val fontRes: Int, val displayName: String) {
    JETBRAINS_MONO(R.font.jetbrains_mono, "JetBrains Mono"),
    FIRA_MONO(R.font.fira_mono, "Fira Mono"),
    HACK(R.font.hack, "Hack"),
}
