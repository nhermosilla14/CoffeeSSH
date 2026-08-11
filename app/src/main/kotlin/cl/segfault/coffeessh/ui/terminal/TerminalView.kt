package cl.segfault.coffeessh.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import cl.segfault.coffeessh.terminal.AnsiColors
import cl.segfault.coffeessh.terminal.Cell
import cl.segfault.coffeessh.terminal.CellAttrs
import cl.segfault.coffeessh.terminal.KeyEncoder
import cl.segfault.coffeessh.terminal.Terminal
import cl.segfault.coffeessh.terminal.TerminalKey
import cl.segfault.coffeessh.terminal.TermColor
import cl.segfault.coffeessh.terminal.TerminalColorScheme

/**
 * Renders a [Terminal]'s grid to a [Canvas] and turns soft/hardware keyboard input into
 * the byte sequences a remote shell expects. Rendering-only and session-agnostic: callers
 * wire [onInput] to the active session's input channel.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var terminal: Terminal? = null
        set(value) {
            field?.onDirty = null
            field = value
            value?.onDirty = {
                post {
                    scrollOffset = scrollOffset.coerceAtMost(value.scrollbackSize)
                    invalidate()
                }
            }
            requestLayout()
            invalidate()
        }

    /** Bytes produced by soft/hardware key input, ready to send to a session. */
    var onInput: ((ByteArray) -> Unit)? = null

    /** Called when the terminal surface is held without starting a scroll. */
    var onLongPress: (() -> Unit)? = null

    /** Called when the terminal surface receives a tap without starting a scroll. */
    var onTap: (() -> Unit)? = null

    /** Called with the selected visible terminal text when selection ends. */
    var onSelectionComplete: ((String) -> Unit)? = null

    /** Fires once, the first time this view knows its real size and has resized [terminal]. */
    var onReady: (() -> Unit)? = null
    private var readyFired = false

    /** Fires after every resize (including the first) with the new grid dimensions - wire
     * this to a session's window-change request. */
    var onResize: ((rows: Int, cols: Int) -> Unit)? = null

    var colorScheme: TerminalColorScheme = TerminalColorScheme.COFFEE
        set(value) { field = value; invalidate() }

    private val defaultFg: Int get() = colorScheme.defaultForeground
    private val defaultBg: Int get() = colorScheme.defaultBackground

    var textSizeSp: Float = 14f
        set(value) {
            field = value
            recomputeMetrics()
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val bgPaint = Paint()
    private val cursorPaint = Paint().apply { alpha = 140 }
    private val selectionPaint = Paint().apply { color = 0x8056C9FF.toInt() }

    private var cellWidth = 0f
    private var cellHeight = 0f
    private var baselineOffset = 0f
    private var scrollOffset = 0
    private var touchDownY = 0f
    private var lastTouchY = 0f
    private var touchMoved = false
    private var longPressTriggered = false
    private var pinching = false
    private var selecting = false
    private var selectionStartRow = 0
    private var selectionStartCol = 0
    private var selectionEndRow = 0
    private var selectionEndCol = 0
    private val longPressRunnable = Runnable {
        if (!touchMoved) {
            longPressTriggered = true
            onLongPress?.invoke()
        }
    }
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinching = true
            touchMoved = true
            removeCallbacks(longPressRunnable)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            textSizeSp = (textSizeSp * detector.scaleFactor).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnClickListener {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
        textPaint.textSize = spToPx(textSizeSp)
        recomputeMetrics()
    }

    fun setFont(@FontRes fontRes: Int) {
        val typeface = runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull()
        textPaint.typeface = typeface ?: Typeface.MONOSPACE
        recomputeMetrics()
    }

    fun beginSelection() {
        selecting = true
        touchMoved = false
        invalidate()
    }

    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

    private fun recomputeMetrics() {
        textPaint.textSize = spToPx(textSizeSp)
        cellWidth = textPaint.measureText("M")
        val fm = textPaint.fontMetrics
        cellHeight = fm.descent - fm.ascent + fm.leading
        baselineOffset = -fm.ascent
        requestLayout()
        updateTerminalSizeFromView()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSizeFromView()
    }

    private fun updateTerminalSizeFromView() {
        if (cellWidth <= 0f || cellHeight <= 0f || width <= 0 || height <= 0) return
        val cols = (width / cellWidth).toInt().coerceAtLeast(1)
        val rows = (height / cellHeight).toInt().coerceAtLeast(1)
        terminal?.resize(rows, cols)
        onResize?.invoke(rows, cols)
        if (!readyFired) {
            readyFired = true
            onReady?.invoke()
        }
    }

    /** Gives the terminal input focus and opens the soft keyboard after layout is ready. */
    fun showKeyboard() {
        requestFocus()
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Prevents the IME from surviving navigation away from the terminal screen. */
    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    override fun onDetachedFromWindow() {
        hideKeyboard()
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(defaultBg)
        val term = terminal ?: return
        val history = term.scrollbackSnapshot()
        val maxOffset = history.size
        val effectiveOffset = scrollOffset.coerceIn(0, maxOffset)
        val firstSourceRow = history.size - effectiveOffset
        for (row in 0 until term.rows) {
            val sourceRow = firstSourceRow + row
            if (sourceRow < history.size) {
                drawHistoryRow(canvas, term, row, history[sourceRow])
            } else if (sourceRow - history.size in 0 until term.rows) {
                drawRow(canvas, term, sourceRow - history.size, row)
            }
        }
        if (selecting) {
            for (row in 0 until term.rows) drawSelectionHighlight(canvas, row)
        }
        if (effectiveOffset == 0 && term.cursorVisible) drawCursor(canvas, term)
    }

    private fun drawHistoryRow(canvas: Canvas, term: Terminal, row: Int, cells: Array<Cell>) {
        val y = row * cellHeight
        var col = 0
        while (col < term.cols) {
            val first = cells.getOrElse(col) { Cell() }
            if (first.width == 0) {
                col++
                continue
            }
            val attrs = first.attrs
            val startCol = col
            val text = StringBuilder()
            while (col < term.cols) {
                val cell = cells.getOrElse(col) { Cell() }
                if (cell.width == 0) {
                    col++
                    continue
                }
                if (cell.attrs != attrs) break
                text.append(if (cell.codePoint == 0) ' ' else String(Character.toChars(cell.codePoint)))
                col += cell.width
            }
            drawRun(canvas, text.toString(), startCol, col, y, attrs)
        }
    }

    private fun drawSelectionHighlight(canvas: Canvas, row: Int) {
        if (!selecting || cellWidth <= 0f) return
        val start = normalizedSelectionStart()
        val end = normalizedSelectionEnd()
        if (row !in start.first..end.first) return
        val firstCol = if (row == start.first) start.second else 0
        val lastCol = if (row == end.first) end.second else (terminal?.cols ?: 0)
        if (lastCol > firstCol) {
            canvas.drawRect(
                firstCol * cellWidth,
                row * cellHeight,
                lastCol * cellWidth,
                (row + 1) * cellHeight,
                selectionPaint,
            )
        }
    }

    private fun normalizedSelectionStart(): Pair<Int, Int> =
        if (selectionStartRow < selectionEndRow ||
            (selectionStartRow == selectionEndRow && selectionStartCol <= selectionEndCol)
        ) {
            selectionStartRow to selectionStartCol
        } else {
            selectionEndRow to selectionEndCol
        }

    private fun normalizedSelectionEnd(): Pair<Int, Int> =
        if (selectionStartRow < selectionEndRow ||
            (selectionStartRow == selectionEndRow && selectionStartCol <= selectionEndCol)
        ) {
            selectionEndRow to selectionEndCol
        } else {
            selectionStartRow to selectionStartCol
        }

    private fun selectedText(): String {
        val term = terminal ?: return ""
        val history = term.scrollbackSnapshot()
        val firstSourceRow = history.size - scrollOffset.coerceIn(0, history.size)
        val start = normalizedSelectionStart()
        val end = normalizedSelectionEnd()
        return (start.first..end.first).joinToString("\n") { row ->
            val sourceRow = firstSourceRow + row
            val cells = if (sourceRow < history.size) history.getOrNull(sourceRow)
            else (sourceRow - history.size).takeIf { it in 0 until term.rows }?.let { terminalRow ->
                Array(term.cols) { col -> term.cellAt(terminalRow, col) }
            }
            val from = if (row == start.first) start.second else 0
            val to = if (row == end.first) end.second else term.cols
            (from until to.coerceAtMost(term.cols)).joinToString("") { col ->
                val cell = cells?.getOrNull(col)
                if (cell == null || cell.codePoint == 0) " " else String(Character.toChars(cell.codePoint))
            }.trimEnd()
        }.trimEnd()
    }

    private fun drawRun(canvas: Canvas, text: String, startCol: Int, endCol: Int, y: Float, attrs: CellAttrs) {
        val x0 = startCol * cellWidth
        val x1 = endCol * cellWidth
        val bg = effectiveBg(attrs)
        if (bg != defaultBg) {
            bgPaint.color = bg
            canvas.drawRect(x0, y, x1, y + cellHeight, bgPaint)
        }
        if (!attrs.invisible) {
            textPaint.color = effectiveFg(attrs)
            textPaint.isFakeBoldText = attrs.bold
            textPaint.isUnderlineText = attrs.underline
            textPaint.isStrikeThruText = attrs.strikethrough
            textPaint.alpha = if (attrs.faint) 160 else 255
            canvas.drawText(text, x0, y + baselineOffset, textPaint)
        }
    }

    private fun drawRow(canvas: Canvas, term: Terminal, terminalRow: Int, visualRow: Int = terminalRow) {
        var col = 0
        val y = visualRow * cellHeight
        while (col < term.cols) {
            val first = term.cellAt(terminalRow, col)
            if (first.width == 0) {
                col++
                continue
            }
            val attrs = first.attrs
            val startCol = col
            val text = StringBuilder()
            var runCol = col
            while (runCol < term.cols) {
                val cell = term.cellAt(terminalRow, runCol)
                if (cell.width == 0) {
                    runCol++
                    continue
                }
                if (cell.attrs != attrs) break
                text.append(if (cell.codePoint == 0) ' ' else String(Character.toChars(cell.codePoint)))
                runCol += cell.width
            }

            val x0 = startCol * cellWidth
            val x1 = runCol * cellWidth
            val bg = effectiveBg(attrs)
            if (bg != defaultBg) {
                bgPaint.color = bg
                canvas.drawRect(x0, y, x1, y + cellHeight, bgPaint)
            }
            if (!attrs.invisible) {
                textPaint.color = effectiveFg(attrs)
                textPaint.isFakeBoldText = attrs.bold
                textPaint.isUnderlineText = attrs.underline
                textPaint.isStrikeThruText = attrs.strikethrough
                textPaint.alpha = if (attrs.faint) 160 else 255
                canvas.drawText(text.toString(), x0, y + baselineOffset, textPaint)
            }
            col = runCol
        }
    }

    private fun effectiveFg(attrs: CellAttrs): Int {
        // Bold renders as the bright variant of an indexed color (classic xterm
        // "boldAsBright" behavior) in addition to the fake-bold stroke.
        val fgColor = attrs.fg
        val spec = if (attrs.bold && fgColor is TermColor.Indexed && fgColor.index < 8) {
            TermColor.Indexed(fgColor.index + 8)
        } else {
            fgColor
        }
        val fg = AnsiColors.resolve(spec, defaultFg, colorScheme.ansi16)
        val bg = AnsiColors.resolve(attrs.bg, defaultBg, colorScheme.ansi16)
        return if (attrs.inverse) bg else fg
    }

    private fun effectiveBg(attrs: CellAttrs): Int {
        val fg = AnsiColors.resolve(attrs.fg, defaultFg, colorScheme.ansi16)
        val bg = AnsiColors.resolve(attrs.bg, defaultBg, colorScheme.ansi16)
        return if (attrs.inverse) fg else bg
    }

    private fun drawCursor(canvas: Canvas, term: Terminal) {
        val x0 = term.cursorCol * cellWidth
        val y0 = term.cursorRow * cellHeight
        cursorPaint.color = defaultFg
        canvas.drawRect(x0, y0, x0 + cellWidth, y0 + cellHeight, cursorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y
                lastTouchY = event.y
                touchMoved = false
                longPressTriggered = false
                pinching = false
                if (selecting) {
                    selectionStartRow = (event.y / cellHeight).toInt().coerceIn(0, (terminal?.rows ?: 1) - 1)
                    selectionStartCol = (event.x / cellWidth).toInt().coerceAtLeast(0)
                    selectionEndRow = selectionStartRow
                    selectionEndCol = selectionStartCol + 1
                    invalidate()
                    return true
                }
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching) return true
                if (selecting) {
                    selectionEndRow = (event.y / cellHeight).toInt().coerceIn(0, (terminal?.rows ?: 1) - 1)
                    selectionEndCol = (event.x / cellWidth).toInt().coerceAtLeast(0)
                    touchMoved = true
                    invalidate()
                    return true
                }
                val delta = lastTouchY - event.y
                if (kotlin.math.abs(event.y - touchDownY) > resources.displayMetrics.density * 8f) {
                    touchMoved = true
                    removeCallbacks(longPressRunnable)
                }
                if (cellHeight > 0f && delta != 0f) {
                    val lines = (delta / cellHeight).toInt()
                    if (lines != 0) {
                        val maxOffset = terminal?.scrollbackSize ?: 0
                        scrollOffset = (scrollOffset - lines).coerceIn(0, maxOffset)
                        lastTouchY -= lines * cellHeight
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (pinching) {
                    pinching = false
                    return true
                }
                if (selecting) {
                    selecting = false
                    onSelectionComplete?.invoke(selectedText())
                    invalidate()
                    return true
                }
                if (!touchMoved && !longPressTriggered) {
                    onTap?.invoke()
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                pinching = false
                return true
            }
        }
        return true
    }

    private companion object {
        const val MIN_TEXT_SIZE_SP = 8f
        const val MAX_TEXT_SIZE_SP = 32f
    }

    // ---------------------------------------------------------------------
    // Input: soft keyboard
    // ---------------------------------------------------------------------

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this)
    }

    private inner class TerminalInputConnection(view: View) : BaseInputConnection(view, false) {
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            // IMEs usually commit Enter as LF, while interactive terminals submit with CR.
            onInput?.invoke(text.toString().replace('\n', '\r').encodeToByteArray())
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            // Many IMEs send this instead of a KeyEvent for backspace.
            repeat(beforeLength) { onInput?.invoke(byteArrayOf(0x7F)) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN && handleHardwareKey(event)) return true
            return super.sendKeyEvent(event)
        }
    }

    // ---------------------------------------------------------------------
    // Input: hardware keyboard
    // ---------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleHardwareKey(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleHardwareKey(event: KeyEvent): Boolean {
        val term = terminal ?: return false
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> TerminalKey.ARROW_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> TerminalKey.ARROW_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> TerminalKey.ARROW_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> TerminalKey.ARROW_RIGHT
            KeyEvent.KEYCODE_MOVE_HOME -> TerminalKey.HOME
            KeyEvent.KEYCODE_MOVE_END -> TerminalKey.END
            KeyEvent.KEYCODE_PAGE_UP -> TerminalKey.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> TerminalKey.PAGE_DOWN
            KeyEvent.KEYCODE_FORWARD_DEL -> TerminalKey.DELETE
            KeyEvent.KEYCODE_INSERT -> TerminalKey.INSERT
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TerminalKey.ENTER
            KeyEvent.KEYCODE_TAB -> TerminalKey.TAB
            KeyEvent.KEYCODE_DEL -> TerminalKey.BACKSPACE
            KeyEvent.KEYCODE_ESCAPE -> TerminalKey.ESCAPE
            else -> null
        }
        if (mapped != null) {
            onInput?.invoke(KeyEncoder.encode(mapped, term))
            return true
        }
        val unicodeChar = event.unicodeChar
        if (unicodeChar == 0) return false
        if (event.isCtrlPressed && unicodeChar.toChar().let { it in 'a'..'z' || it in 'A'..'Z' }) {
            onInput?.invoke(KeyEncoder.encodeCtrl(unicodeChar.toChar()))
            return true
        }
        if (!event.isCtrlPressed) {
            onInput?.invoke(String(Character.toChars(unicodeChar)).encodeToByteArray())
            return true
        }
        return false
    }
}
