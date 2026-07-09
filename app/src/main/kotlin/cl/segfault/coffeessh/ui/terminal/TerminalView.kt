package cl.segfault.coffeessh.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import cl.segfault.coffeessh.terminal.AnsiColors
import cl.segfault.coffeessh.terminal.CellAttrs
import cl.segfault.coffeessh.terminal.KeyEncoder
import cl.segfault.coffeessh.terminal.Terminal
import cl.segfault.coffeessh.terminal.TerminalKey
import cl.segfault.coffeessh.terminal.TermColor

/**
 * Renders a [Terminal]'s grid to a [Canvas] and turns soft/hardware keyboard input into
 * the byte sequences a remote shell expects. Rendering-only and session-agnostic: callers
 * wire [onInput] to wherever bytes should go (a demo shell in M2, an SSH channel in M3).
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var terminal: Terminal? = null
        set(value) {
            field?.onDirty = null
            field = value
            value?.onDirty = { post { invalidate() } }
            requestLayout()
            invalidate()
        }

    /** Bytes produced by soft/hardware key input, ready to send to a session. */
    var onInput: ((ByteArray) -> Unit)? = null

    /** Fires once, the first time this view knows its real size and has resized [terminal]. */
    var onReady: (() -> Unit)? = null
    private var readyFired = false

    /** Fires after every resize (including the first) with the new grid dimensions - wire
     * this to a session's window-change request; safe to leave null (e.g. the M2 demo). */
    var onResize: ((rows: Int, cols: Int) -> Unit)? = null

    var defaultFg: Int = 0xFFE8E0D6.toInt()
        set(value) { field = value; invalidate() }
    var defaultBg: Int = 0xFF1A120C.toInt()
        set(value) { field = value; invalidate() }

    var textSizeSp: Float = 14f
        set(value) {
            field = value
            recomputeMetrics()
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val bgPaint = Paint()
    private val cursorPaint = Paint().apply { alpha = 140 }

    private var cellWidth = 0f
    private var cellHeight = 0f
    private var baselineOffset = 0f

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

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(defaultBg)
        val term = terminal ?: return
        for (row in 0 until term.rows) drawRow(canvas, term, row)
        if (term.cursorVisible) drawCursor(canvas, term)
    }

    private fun drawRow(canvas: Canvas, term: Terminal, row: Int) {
        var col = 0
        val y = row * cellHeight
        while (col < term.cols) {
            val first = term.cellAt(row, col)
            if (first.width == 0) {
                col++
                continue
            }
            val attrs = first.attrs
            val startCol = col
            val text = StringBuilder()
            var runCol = col
            while (runCol < term.cols) {
                val cell = term.cellAt(row, runCol)
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
        val fg = AnsiColors.resolve(spec, defaultFg)
        val bg = AnsiColors.resolve(attrs.bg, defaultBg)
        return if (attrs.inverse) bg else fg
    }

    private fun effectiveBg(attrs: CellAttrs): Int {
        val fg = AnsiColors.resolve(attrs.fg, defaultFg)
        val bg = AnsiColors.resolve(attrs.bg, defaultBg)
        return if (attrs.inverse) fg else bg
    }

    private fun drawCursor(canvas: Canvas, term: Terminal) {
        val x0 = term.cursorCol * cellWidth
        val y0 = term.cursorRow * cellHeight
        cursorPaint.color = defaultFg
        canvas.drawRect(x0, y0, x0 + cellWidth, y0 + cellHeight, cursorPaint)
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
            onInput?.invoke(text.toString().encodeToByteArray())
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
