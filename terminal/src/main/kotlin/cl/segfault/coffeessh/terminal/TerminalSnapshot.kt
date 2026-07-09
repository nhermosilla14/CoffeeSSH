package cl.segfault.coffeessh.terminal

/** Plain-text snapshot of the currently visible screen (e.g. for a "copy screen" action). */
fun Terminal.snapshotText(): String {
    val lines = ArrayList<String>(rows)
    for (row in 0 until rows) {
        val sb = StringBuilder()
        for (col in 0 until cols) {
            val cell = cellAt(row, col)
            if (cell.width == 0) continue
            sb.append(if (cell.codePoint == 0) ' ' else String(Character.toChars(cell.codePoint)))
        }
        lines += sb.toString().trimEnd()
    }
    return lines.joinToString("\n")
}
