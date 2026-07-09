package cl.segfault.coffeessh.terminal

/**
 * A fixed-size grid of [Cell]s for one "page" (either the main screen or the alternate
 * screen). All operations are expressed directly in terms of row/column indices and an
 * optional scrolling region so [Terminal] can stay a thin dispatcher on top of this.
 */
internal class Screen(rows: Int, cols: Int) {
    var rows: Int = rows
        private set
    var cols: Int = cols
        private set

    private var lines: Array<Array<Cell>> = Array(rows) { blankRow(cols) }

    private fun blankRow(width: Int, attrs: CellAttrs = CellAttrs()): Array<Cell> =
        Array(width) { Cell.blank(attrs) }

    fun cell(row: Int, col: Int): Cell = lines[row][col]

    fun setCell(row: Int, col: Int, cell: Cell) {
        lines[row][col] = cell
    }

    fun row(row: Int): Array<Cell> = lines[row]

    fun clearRow(row: Int, attrs: CellAttrs = CellAttrs()) {
        lines[row] = blankRow(cols, attrs)
    }

    fun clearAll(attrs: CellAttrs = CellAttrs()) {
        for (r in 0 until rows) clearRow(r, attrs)
    }

    fun eraseInLine(row: Int, fromCol: Int, toCol: Int, attrs: CellAttrs) {
        val line = lines[row]
        for (c in fromCol..toCol) {
            if (c in 0 until cols) line[c] = Cell.blank(attrs)
        }
    }

    /** Shifts region [top..bottom] up by [n], discarding the top [n] lines of the region. */
    fun scrollUp(top: Int, bottom: Int, n: Int, attrs: CellAttrs, onDiscarded: ((Array<Cell>) -> Unit)? = null) {
        val count = n.coerceAtMost(bottom - top + 1)
        repeat(count) {
            onDiscarded?.invoke(lines[top])
            for (r in top until bottom) lines[r] = lines[r + 1]
            lines[bottom] = blankRow(cols, attrs)
        }
    }

    /** Shifts region [top..bottom] down by [n], discarding the bottom [n] lines of the region. */
    fun scrollDown(top: Int, bottom: Int, n: Int, attrs: CellAttrs) {
        val count = n.coerceAtMost(bottom - top + 1)
        repeat(count) {
            for (r in bottom downTo top + 1) lines[r] = lines[r - 1]
            lines[top] = blankRow(cols, attrs)
        }
    }

    /** Inserts [n] blank lines at [row], pushing lines down and off the bottom of [bottom]. */
    fun insertLines(row: Int, bottom: Int, n: Int, attrs: CellAttrs) = scrollDown(row, bottom, n, attrs)

    /** Deletes [n] lines at [row], pulling lines in [row+1..bottom] up. */
    fun deleteLines(row: Int, bottom: Int, n: Int, attrs: CellAttrs) = scrollUp(row, bottom, n, attrs)

    fun insertChars(row: Int, col: Int, n: Int, attrs: CellAttrs) {
        val line = lines[row]
        val count = n.coerceAtMost(cols - col)
        for (c in cols - 1 downTo col + count) line[c] = line[c - count]
        for (c in col until (col + count).coerceAtMost(cols)) line[c] = Cell.blank(attrs)
    }

    fun deleteChars(row: Int, col: Int, n: Int, attrs: CellAttrs) {
        val line = lines[row]
        val count = n.coerceAtMost(cols - col)
        for (c in col until cols - count) line[c] = line[c + count]
        for (c in (cols - count).coerceAtLeast(col) until cols) line[c] = Cell.blank(attrs)
    }

    /** Resizes in place, preserving existing content in the top-left corner. */
    fun resize(newRows: Int, newCols: Int, attrs: CellAttrs = CellAttrs()) {
        val newLines = Array(newRows) { r ->
            if (r < rows) {
                Array(newCols) { c -> if (c < cols) lines[r][c] else Cell.blank(attrs) }
            } else {
                blankRow(newCols, attrs)
            }
        }
        lines = newLines
        rows = newRows
        cols = newCols
    }
}
