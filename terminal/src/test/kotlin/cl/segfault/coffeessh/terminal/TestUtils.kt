package cl.segfault.coffeessh.terminal

/** Renders row [row] as plain text (spaces for blanks), ignoring width-0 continuation cells. */
fun Terminal.rowText(row: Int): String {
    val sb = StringBuilder()
    for (c in 0 until cols) {
        val cell = cellAt(row, c)
        if (cell.width == 0) continue
        sb.append(if (cell.codePoint == 0) ' ' else String(Character.toChars(cell.codePoint)))
    }
    return sb.toString()
}

fun Terminal.rowTrimmed(row: Int): String = rowText(row).trimEnd()
