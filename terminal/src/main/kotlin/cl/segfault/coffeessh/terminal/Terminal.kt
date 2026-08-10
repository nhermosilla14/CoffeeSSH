package cl.segfault.coffeessh.terminal

/** Cursor position + attributes snapshot for DECSC/DECRC (`ESC 7`/`ESC 8`) and `CSI s`/`CSI u`. */
private data class SavedCursor(
    val row: Int,
    val col: Int,
    val attrs: CellAttrs,
    val originMode: Boolean,
)

private enum class Charset { ASCII, SPECIAL_GRAPHICS }

/**
 * A VT100/xterm-256color-compatible terminal emulator: feed it raw bytes from a shell/SSH
 * session via [write], then read [cellAt] (or [scrollbackLine]) to render.
 *
 * This class owns all VT100 *semantics*; [EscapeSequenceParser] only tokenizes the byte
 * stream into print/execute/CSI/ESC/OSC actions, which are dispatched back here.
 *
 * Known, deliberate simplifications (documented in PLAN.md):
 *  - DCS/SOS/PM/APC payloads are recognized but discarded (no sixel/termcap-query support).
 *  - Combining marks are dropped rather than merged into the preceding cell.
 *  - No scrollback reflow on resize, no left/right margins (DECSLRM), no mouse reporting.
 */
class Terminal(rows: Int, cols: Int, scrollbackLines: Int = 5000) : ParserSink {

    var rows: Int = rows
        private set
    var cols: Int = cols
        private set

    private val main = Screen(rows, cols)
    private val alt = Screen(rows, cols)
    private var current: Screen = main
    val scrollback = Scrollback(scrollbackLines)

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set
    private var pendingWrap = false

    private var currentAttrs = CellAttrs()

    var scrollTop: Int = 0
        private set
    var scrollBottom: Int = rows - 1
        private set

    val modes = Modes()
    var title: String = ""
        private set

    private var g0Charset = Charset.ASCII
    private var g1Charset = Charset.ASCII
    private var invokedSlot = 0 // 0 -> G0, 1 -> G1
    private var tabStops = BooleanArray(cols) { it % 8 == 0 }

    private var savedCursor: SavedCursor? = null
    private var altSwitchSavedCursor: SavedCursor? = null

    /** Bytes the terminal needs to send back upstream (CPR/DA replies) - wired to the SSH channel in M3. */
    var onResponse: ((ByteArray) -> Unit)? = null
    var onTitleChange: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null

    /** Invoked after every [write] (and [resize]) so a view can schedule a redraw. */
    var onDirty: (() -> Unit)? = null

    private val parser = EscapeSequenceParser(this)
    private val utf8 = Utf8Decoder()

    fun write(bytes: ByteArray) {
        utf8.decode(bytes) { cp -> parser.feed(cp) }
        onDirty?.invoke()
    }

    fun write(text: String) = write(text.encodeToByteArray())

    fun cellAt(row: Int, col: Int): Cell = current.cell(row, col)

    val cursorVisible: Boolean get() = modes.cursorVisible
    val altScreenActive: Boolean get() = modes.altScreenActive

    fun scrollbackLine(indexFromTop: Int): Array<Cell> = scrollback.lineFromTop(indexFromTop)
    fun scrollbackSnapshot(): List<Array<Cell>> = scrollback.snapshot()
    val scrollbackSize: Int get() = if (modes.altScreenActive) 0 else scrollback.size

    fun resize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == cols) return
        main.resize(newRows, newCols)
        alt.resize(newRows, newCols)
        rows = newRows
        cols = newCols
        scrollTop = scrollTop.coerceIn(0, rows - 1)
        scrollBottom = (if (scrollBottom >= rows - 1) rows - 1 else scrollBottom).coerceIn(scrollTop, rows - 1)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        tabStops = BooleanArray(cols) { it % 8 == 0 }
        pendingWrap = false
        onDirty?.invoke()
    }

    // ---------------------------------------------------------------------
    // ParserSink
    // ---------------------------------------------------------------------

    override fun onPrint(codePoint: Int) {
        val translated = if (activeCharset() == Charset.SPECIAL_GRAPHICS) {
            DecSpecialGraphics.translate(codePoint)
        } else {
            codePoint
        }
        val width = charWidth(translated)
        if (width == 0) return // combining mark: dropped, see class doc

        if (pendingWrap) {
            cursorCol = 0
            lineFeed()
            pendingWrap = false
        }

        if (modes.insertMode) {
            current.insertChars(cursorRow, cursorCol, width, currentAttrs)
        }

        current.setCell(cursorRow, cursorCol, Cell(translated, currentAttrs, width))
        if (width == 2 && cursorCol + 1 < cols) {
            current.setCell(cursorRow, cursorCol + 1, Cell(0, currentAttrs, 0))
        }

        if (cursorCol + width >= cols) {
            cursorCol = cols - 1
            if (modes.autowrap) pendingWrap = true
        } else {
            cursorCol += width
        }
    }

    override fun onExecute(b: Int) {
        when (b) {
            0x07 -> onBell?.invoke()
            0x08 -> {
                if (cursorCol > 0) cursorCol--
                pendingWrap = false
            }
            0x09 -> {
                cursorCol = nextTabStop(cursorCol)
                pendingWrap = false
            }
            0x0A, 0x0B, 0x0C -> {
                lineFeed()
                pendingWrap = false
            }
            0x0D -> {
                cursorCol = 0
                pendingWrap = false
            }
            0x0E -> invokedSlot = 1
            0x0F -> invokedSlot = 0
            else -> {}
        }
    }

    override fun onEscDispatch(finalByte: Char, intermediates: String) {
        when {
            intermediates == "(" -> g0Charset = charsetFor(finalByte)
            intermediates == ")" -> g1Charset = charsetFor(finalByte)
            intermediates.isNotEmpty() -> {} // other charset slots (G2/G3) not implemented
            finalByte == '7' -> saveCursor()
            finalByte == '8' -> restoreCursor()
            finalByte == 'c' -> fullReset()
            finalByte == 'D' -> {
                lineFeed()
                pendingWrap = false
            }
            finalByte == 'M' -> {
                reverseIndex()
                pendingWrap = false
            }
            finalByte == 'E' -> {
                cursorCol = 0
                lineFeed()
                pendingWrap = false
            }
            finalByte == 'H' -> tabStops[cursorCol] = true
            finalByte == '=' -> modes.applicationKeypad = true
            finalByte == '>' -> modes.applicationKeypad = false
            else -> {}
        }
    }

    override fun onOscDispatch(data: String) {
        val semi = data.indexOf(';')
        if (semi < 0) return
        val num = data.substring(0, semi).toIntOrNull() ?: return
        val text = data.substring(semi + 1)
        if (num == 0 || num == 1 || num == 2) {
            title = text
            onTitleChange?.invoke(text)
        }
    }

    override fun onCsiDispatch(finalByte: Char, params: IntArray, prefix: Char, intermediates: String) {
        fun p(i: Int, default: Int): Int {
            val v = params.getOrElse(i) { default }
            return if (v == 0) default else v
        }

        when (finalByte) {
            'A' -> { cursorRow = (cursorRow - p(0, 1)).coerceAtLeast(vertBoundTop()); pendingWrap = false }
            'B' -> { cursorRow = (cursorRow + p(0, 1)).coerceAtMost(vertBoundBottom()); pendingWrap = false }
            'C' -> { cursorCol = (cursorCol + p(0, 1)).coerceAtMost(cols - 1); pendingWrap = false }
            'D' -> { cursorCol = (cursorCol - p(0, 1)).coerceAtLeast(0); pendingWrap = false }
            'E' -> { cursorRow = (cursorRow + p(0, 1)).coerceAtMost(rows - 1); cursorCol = 0; pendingWrap = false }
            'F' -> { cursorRow = (cursorRow - p(0, 1)).coerceAtLeast(0); cursorCol = 0; pendingWrap = false }
            'G', '`' -> { cursorCol = (p(0, 1) - 1).coerceIn(0, cols - 1); pendingWrap = false }
            'H', 'f' -> { moveCursorTo(p(0, 1) - 1, p(1, 1) - 1); pendingWrap = false }
            'd' -> { cursorRow = originAdjustedRow(p(0, 1) - 1); pendingWrap = false }
            'e' -> { cursorRow = (cursorRow + p(0, 1)).coerceAtMost(rows - 1); pendingWrap = false }
            'a' -> { cursorCol = (cursorCol + p(0, 1)).coerceAtMost(cols - 1); pendingWrap = false }
            'J' -> eraseInDisplay(if (params.isEmpty()) 0 else params[0])
            'K' -> eraseInLine(if (params.isEmpty()) 0 else params[0])
            'L' -> current.insertLines(cursorRow.coerceIn(scrollTop, scrollBottom), scrollBottom, p(0, 1), currentAttrs)
            'M' -> current.deleteLines(cursorRow.coerceIn(scrollTop, scrollBottom), scrollBottom, p(0, 1), currentAttrs)
            '@' -> current.insertChars(cursorRow, cursorCol, p(0, 1), currentAttrs)
            'P' -> current.deleteChars(cursorRow, cursorCol, p(0, 1), currentAttrs)
            'X' -> current.eraseInLine(cursorRow, cursorCol, (cursorCol + p(0, 1) - 1).coerceAtMost(cols - 1), currentAttrs)
            'S' -> scrollRegionUp(p(0, 1))
            'T' -> scrollRegionDown(p(0, 1))
            'r' -> setScrollRegion(p(0, 1) - 1, p(1, rows) - 1)
            'm' -> applySgr(params)
            'h' -> setModes(params, prefix, true)
            'l' -> setModes(params, prefix, false)
            's' -> if (prefix != '?') saveCursor()
            'u' -> if (prefix != '?') restoreCursor()
            'n' -> handleDeviceStatusReport(if (params.isEmpty()) 0 else params[0])
            'c' -> if (prefix != '>') handleDeviceAttributes()
            'g' -> clearTabStops(if (params.isEmpty()) 0 else params[0])
            else -> {}
        }
    }

    // ---------------------------------------------------------------------
    // Cursor / scrolling helpers
    // ---------------------------------------------------------------------

    private fun vertBoundTop(): Int = if (modes.originMode) scrollTop else 0
    private fun vertBoundBottom(): Int = if (modes.originMode) scrollBottom else rows - 1

    private fun originAdjustedRow(row: Int): Int =
        if (modes.originMode) (scrollTop + row).coerceIn(scrollTop, scrollBottom) else row.coerceIn(0, rows - 1)

    private fun moveCursorTo(row: Int, col: Int) {
        cursorRow = originAdjustedRow(row)
        cursorCol = col.coerceIn(0, cols - 1)
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollRegionUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            scrollRegionDown(1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun scrollRegionUp(n: Int) {
        current.scrollUp(scrollTop, scrollBottom, n, currentAttrs) { discarded ->
            if (!modes.altScreenActive && scrollTop == 0) scrollback.push(discarded)
        }
    }

    private fun scrollRegionDown(n: Int) {
        current.scrollDown(scrollTop, scrollBottom, n, currentAttrs)
    }

    private fun setScrollRegion(top: Int, bottom: Int) {
        if (bottom > top && top >= 0 && bottom < rows) {
            scrollTop = top
            scrollBottom = bottom
        } else {
            scrollTop = 0
            scrollBottom = rows - 1
        }
        cursorRow = if (modes.originMode) scrollTop else 0
        cursorCol = 0
        pendingWrap = false
    }

    // ---------------------------------------------------------------------
    // Erasing
    // ---------------------------------------------------------------------

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                current.eraseInLine(cursorRow, cursorCol, cols - 1, currentAttrs)
                for (r in cursorRow + 1 until rows) current.clearRow(r, currentAttrs)
            }
            1 -> {
                current.eraseInLine(cursorRow, 0, cursorCol, currentAttrs)
                for (r in 0 until cursorRow) current.clearRow(r, currentAttrs)
            }
            2 -> current.clearAll(currentAttrs)
            3 -> {
                current.clearAll(currentAttrs)
                if (!modes.altScreenActive) scrollback.clear()
            }
            else -> {}
        }
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> current.eraseInLine(cursorRow, cursorCol, cols - 1, currentAttrs)
            1 -> current.eraseInLine(cursorRow, 0, cursorCol, currentAttrs)
            2 -> current.eraseInLine(cursorRow, 0, cols - 1, currentAttrs)
            else -> {}
        }
    }

    // ---------------------------------------------------------------------
    // SGR
    // ---------------------------------------------------------------------

    private fun applySgr(params: IntArray) {
        if (params.isEmpty()) {
            currentAttrs = CellAttrs()
            return
        }
        var attrs = currentAttrs
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> attrs = CellAttrs()
                1 -> attrs = attrs.copy(bold = true)
                2 -> attrs = attrs.copy(faint = true)
                3 -> attrs = attrs.copy(italic = true)
                4 -> attrs = attrs.copy(underline = true)
                5, 6 -> attrs = attrs.copy(blink = true)
                7 -> attrs = attrs.copy(inverse = true)
                8 -> attrs = attrs.copy(invisible = true)
                9 -> attrs = attrs.copy(strikethrough = true)
                22 -> attrs = attrs.copy(bold = false, faint = false)
                23 -> attrs = attrs.copy(italic = false)
                24 -> attrs = attrs.copy(underline = false)
                25 -> attrs = attrs.copy(blink = false)
                27 -> attrs = attrs.copy(inverse = false)
                28 -> attrs = attrs.copy(invisible = false)
                29 -> attrs = attrs.copy(strikethrough = false)
                in 30..37 -> attrs = attrs.copy(fg = TermColor.Indexed(p - 30))
                38 -> {
                    val (color, consumed) = parseExtendedColor(params, i + 1)
                    attrs = attrs.copy(fg = color)
                    i += consumed
                }
                39 -> attrs = attrs.copy(fg = TermColor.Default)
                in 40..47 -> attrs = attrs.copy(bg = TermColor.Indexed(p - 40))
                48 -> {
                    val (color, consumed) = parseExtendedColor(params, i + 1)
                    attrs = attrs.copy(bg = color)
                    i += consumed
                }
                49 -> attrs = attrs.copy(bg = TermColor.Default)
                in 90..97 -> attrs = attrs.copy(fg = TermColor.Indexed(p - 90 + 8))
                in 100..107 -> attrs = attrs.copy(bg = TermColor.Indexed(p - 100 + 8))
                else -> {}
            }
            i++
        }
        currentAttrs = attrs
    }

    private fun parseExtendedColor(params: IntArray, start: Int): Pair<TermColor, Int> {
        if (start >= params.size) return TermColor.Default to 0
        return when (params[start]) {
            5 -> if (start + 1 < params.size) TermColor.Indexed(params[start + 1]) to 2 else TermColor.Default to 1
            2 -> if (start + 3 < params.size) {
                TermColor.Rgb(params[start + 1], params[start + 2], params[start + 3]) to 4
            } else {
                TermColor.Default to 1
            }
            else -> TermColor.Default to 1
        }
    }

    // ---------------------------------------------------------------------
    // Modes
    // ---------------------------------------------------------------------

    private fun setModes(params: IntArray, prefix: Char, enable: Boolean) {
        for (p in params) {
            if (prefix == '?') {
                when (p) {
                    1 -> modes.applicationCursorKeys = enable
                    5 -> modes.reverseVideo = enable
                    6 -> {
                        modes.originMode = enable
                        cursorRow = if (enable) scrollTop else 0
                        cursorCol = 0
                    }
                    7 -> modes.autowrap = enable
                    25 -> modes.cursorVisible = enable
                    47, 1047 -> switchAltScreen(enable, saveCursor = false, clearOnEnable = true)
                    1048 -> if (enable) saveCursor() else restoreCursor()
                    1049 -> switchAltScreen(enable, saveCursor = true, clearOnEnable = true)
                    2004 -> modes.bracketedPaste = enable
                    else -> {} // mouse tracking (1000/1002/1003/1005/1006/...) not yet implemented
                }
            } else {
                when (p) {
                    4 -> modes.insertMode = enable
                    else -> {}
                }
            }
        }
    }

    private fun switchAltScreen(enable: Boolean, saveCursor: Boolean, clearOnEnable: Boolean) {
        if (enable == modes.altScreenActive) return
        if (enable) {
            if (saveCursor) altSwitchSavedCursor = SavedCursor(cursorRow, cursorCol, currentAttrs, modes.originMode)
            current = alt
            if (clearOnEnable) alt.clearAll(CellAttrs())
            modes.altScreenActive = true
            cursorRow = 0
            cursorCol = 0
            pendingWrap = false
        } else {
            current = main
            modes.altScreenActive = false
            if (saveCursor) {
                altSwitchSavedCursor?.let {
                    cursorRow = it.row.coerceIn(0, rows - 1)
                    cursorCol = it.col.coerceIn(0, cols - 1)
                    currentAttrs = it.attrs
                    modes.originMode = it.originMode
                }
            }
            pendingWrap = false
        }
    }

    private fun saveCursor() {
        savedCursor = SavedCursor(cursorRow, cursorCol, currentAttrs, modes.originMode)
    }

    private fun restoreCursor() {
        savedCursor?.let {
            cursorRow = it.row.coerceIn(0, rows - 1)
            cursorCol = it.col.coerceIn(0, cols - 1)
            currentAttrs = it.attrs
            modes.originMode = it.originMode
            pendingWrap = false
        }
    }

    private fun fullReset() {
        main.clearAll()
        alt.clearAll()
        current = main
        scrollback.clear()
        cursorRow = 0
        cursorCol = 0
        pendingWrap = false
        currentAttrs = CellAttrs()
        scrollTop = 0
        scrollBottom = rows - 1
        savedCursor = null
        altSwitchSavedCursor = null
        g0Charset = Charset.ASCII
        g1Charset = Charset.ASCII
        invokedSlot = 0
        tabStops = BooleanArray(cols) { it % 8 == 0 }
        title = ""
        // Replace modes with defaults without allocating a new (private) instance.
        modes.autowrap = true
        modes.originMode = false
        modes.insertMode = false
        modes.cursorVisible = true
        modes.applicationCursorKeys = false
        modes.applicationKeypad = false
        modes.bracketedPaste = false
        modes.altScreenActive = false
        modes.reverseVideo = false
    }

    // ---------------------------------------------------------------------
    // Tabs, charsets, replies
    // ---------------------------------------------------------------------

    private fun nextTabStop(from: Int): Int {
        for (c in (from + 1) until cols) if (tabStops[c]) return c
        return cols - 1
    }

    private fun clearTabStops(mode: Int) {
        when (mode) {
            0 -> tabStops[cursorCol] = false
            3 -> tabStops.fill(false)
            else -> {}
        }
    }

    private fun charsetFor(finalByte: Char): Charset =
        if (finalByte == '0') Charset.SPECIAL_GRAPHICS else Charset.ASCII

    private fun activeCharset(): Charset = if (invokedSlot == 0) g0Charset else g1Charset

    private fun handleDeviceStatusReport(mode: Int) {
        when (mode) {
            5 -> onResponse?.invoke("\u001b[0n".encodeToByteArray())
            6 -> {
                val row = (if (modes.originMode) cursorRow - scrollTop else cursorRow) + 1
                val col = cursorCol + 1
                onResponse?.invoke("\u001b[$row;${col}R".encodeToByteArray())
            }
            else -> {}
        }
    }

    private fun handleDeviceAttributes() {
        onResponse?.invoke("\u001b[?1;2c".encodeToByteArray())
    }

    companion object {
        /** `TERM` value CoffeeSSH reports to remote hosts. */
        const val TERM_TYPE: String = "xterm-256color"

        internal fun charWidth(cp: Int): Int = when {
            isCombining(cp) -> 0
            isWide(cp) -> 2
            else -> 1
        }

        private fun isCombining(cp: Int): Boolean =
            cp in 0x0300..0x036F ||
                cp in 0x1AB0..0x1AFF ||
                cp in 0x1DC0..0x1DFF ||
                cp in 0x20D0..0x20FF ||
                cp in 0xFE20..0xFE2F

        private fun isWide(cp: Int): Boolean =
            cp in 0x1100..0x115F ||
                cp in 0x2E80..0x303E ||
                cp in 0x3041..0x33FF ||
                cp in 0x3400..0x4DBF ||
                cp in 0x4E00..0x9FFF ||
                cp in 0xA000..0xA4CF ||
                cp in 0xAC00..0xD7A3 ||
                cp in 0xF900..0xFAFF ||
                cp in 0xFE30..0xFE4F ||
                cp in 0xFF00..0xFF60 ||
                cp in 0xFFE0..0xFFE6 ||
                cp in 0x20000..0x2FFFD ||
                cp in 0x30000..0x3FFFD
    }
}
