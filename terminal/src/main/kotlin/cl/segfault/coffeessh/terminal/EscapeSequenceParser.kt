package cl.segfault.coffeessh.terminal

/** Receives fully-tokenized actions from [EscapeSequenceParser]. */
internal interface ParserSink {
    fun onPrint(codePoint: Int)
    fun onExecute(b: Int)
    fun onCsiDispatch(finalByte: Char, params: IntArray, prefix: Char, intermediates: String)
    fun onEscDispatch(finalByte: Char, intermediates: String)
    fun onOscDispatch(data: String)
}

/**
 * Tokenizer for the classic DEC/xterm control sequence grammar, modeled after the
 * well-known VT500 parser state diagram (vt100.net/emu/dec_ansi_parser). Operates on
 * already UTF-8-decoded code points (see [Utf8Decoder]) since every syntax byte in this
 * grammar (ESC, CSI, parameters, final bytes) is plain ASCII.
 *
 * DCS/SOS/PM/APC payloads are recognized structurally (so they don't leak into the
 * grid as garbage) but their content is discarded — full DCS support is out of scope
 * for now (see PLAN.md).
 */
internal class EscapeSequenceParser(private val sink: ParserSink) {

    private enum class State {
        GROUND, ESCAPE, ESCAPE_INTERMEDIATE,
        CSI_ENTRY, CSI_PARAM, CSI_INTERMEDIATE, CSI_IGNORE,
        OSC_STRING,
        DCS_PASSTHROUGH,
    }

    private var state = State.GROUND
    private val params = ArrayList<Int>()
    private var currentParam = -1
    private var prefix = '\u0000'
    private val intermediates = StringBuilder()
    private val oscBuffer = StringBuilder()

    fun feed(codePoints: IntArray) {
        for (cp in codePoints) step(cp)
    }

    fun feed(codePoint: Int) = step(codePoint)

    private fun reset() {
        state = State.GROUND
        params.clear()
        currentParam = -1
        prefix = '\u0000'
        intermediates.setLength(0)
    }

    private fun step(cp: Int) {
        // ESC always aborts whatever came before and (re)starts an escape sequence,
        // and CAN/SUB (0x18/0x1A) always abort back to ground - both match real terminals.
        if (cp == 0x1B) {
            // String Terminator is `ESC \\`; if we were mid-OSC, this ESC is its first byte,
            // so flush now (the following '\\' lands in ESCAPE state as a harmless no-op
            // dispatch, matching real terminals where a bare ST is simply ignored).
            if (state == State.OSC_STRING) {
                sink.onOscDispatch(oscBuffer.toString())
            }
            state = State.ESCAPE
            params.clear()
            currentParam = -1
            prefix = '\u0000'
            intermediates.setLength(0)
            return
        }
        if (cp == 0x18 || cp == 0x1A) {
            reset()
            return
        }
        when (state) {
            State.GROUND -> stepGround(cp)
            State.ESCAPE -> stepEscape(cp)
            State.ESCAPE_INTERMEDIATE -> stepEscapeIntermediate(cp)
            State.CSI_ENTRY -> stepCsiEntry(cp)
            State.CSI_PARAM -> stepCsiParam(cp)
            State.CSI_INTERMEDIATE -> stepCsiIntermediate(cp)
            State.CSI_IGNORE -> stepCsiIgnore(cp)
            State.OSC_STRING -> stepOscString(cp)
            State.DCS_PASSTHROUGH -> stepDcsPassthrough(cp)
        }
    }

    private fun isC0(cp: Int) = cp in 0x00..0x1F

    private fun stepGround(cp: Int) {
        if (isC0(cp)) {
            sink.onExecute(cp)
        } else {
            sink.onPrint(cp)
        }
    }

    private fun stepEscape(cp: Int) {
        when {
            isC0(cp) -> sink.onExecute(cp)
            cp == '['.code -> state = State.CSI_ENTRY
            cp == ']'.code -> {
                oscBuffer.setLength(0)
                state = State.OSC_STRING
            }
            cp == 'P'.code || cp == 'X'.code || cp == '^'.code || cp == '_'.code -> {
                // DCS / SOS / PM / APC: swallow until ST (ESC \) or BEL.
                state = State.DCS_PASSTHROUGH
            }
            cp in 0x20..0x2F -> {
                intermediates.append(cp.toChar())
                state = State.ESCAPE_INTERMEDIATE
            }
            cp in 0x30..0x7E -> {
                sink.onEscDispatch(cp.toChar(), intermediates.toString())
                reset()
            }
            else -> reset()
        }
    }

    private fun stepEscapeIntermediate(cp: Int) {
        when {
            isC0(cp) -> sink.onExecute(cp)
            cp in 0x20..0x2F -> intermediates.append(cp.toChar())
            cp in 0x30..0x7E -> {
                sink.onEscDispatch(cp.toChar(), intermediates.toString())
                reset()
            }
            else -> reset()
        }
    }

    private fun beginParam() {
        if (currentParam < 0) currentParam = 0
    }

    private fun stepCsiEntry(cp: Int) {
        when {
            isC0(cp) -> sink.onExecute(cp)
            cp in '0'.code..'9'.code -> {
                beginParam()
                currentParam = currentParam * 10 + (cp - '0'.code)
                state = State.CSI_PARAM
            }
            cp == ';'.code -> {
                params.add(if (currentParam < 0) 0 else currentParam)
                currentParam = -1
                state = State.CSI_PARAM
            }
            cp == '?'.code || cp == '>'.code || cp == '='.code -> {
                prefix = cp.toChar()
                state = State.CSI_PARAM
            }
            cp in 0x20..0x2F -> {
                intermediates.append(cp.toChar())
                state = State.CSI_INTERMEDIATE
            }
            cp in 0x40..0x7E -> dispatchCsi(cp)
            else -> reset()
        }
    }

    private fun stepCsiParam(cp: Int) {
        when {
            isC0(cp) -> sink.onExecute(cp)
            cp in '0'.code..'9'.code -> {
                beginParam()
                currentParam = currentParam * 10 + (cp - '0'.code)
            }
            cp == ';'.code || cp == ':'.code -> {
                params.add(if (currentParam < 0) 0 else currentParam)
                currentParam = -1
            }
            cp in 0x20..0x2F -> {
                intermediates.append(cp.toChar())
                state = State.CSI_INTERMEDIATE
            }
            cp in 0x40..0x7E -> dispatchCsi(cp)
            else -> {
                state = State.CSI_IGNORE
            }
        }
    }

    private fun stepCsiIntermediate(cp: Int) {
        when {
            isC0(cp) -> sink.onExecute(cp)
            cp in 0x20..0x2F -> intermediates.append(cp.toChar())
            cp in 0x40..0x7E -> dispatchCsi(cp)
            else -> state = State.CSI_IGNORE
        }
    }

    private fun stepCsiIgnore(cp: Int) {
        when {
            isC0(cp) -> sink.onExecute(cp)
            cp in 0x40..0x7E -> reset()
            else -> {} // stay ignoring until a final byte shows up
        }
    }

    private fun dispatchCsi(finalByte: Int) {
        if (currentParam >= 0) params.add(currentParam)
        sink.onCsiDispatch(finalByte.toChar(), params.toIntArray(), prefix, intermediates.toString())
        reset()
    }

    private fun stepOscString(cp: Int) {
        if (cp == 0x07) { // BEL terminates OSC too (xterm convention)
            sink.onOscDispatch(oscBuffer.toString())
            reset()
        } else if (oscBuffer.length < MAX_OSC_LENGTH) {
            oscBuffer.append(cp.toChar())
        }
    }

    private fun stepDcsPassthrough(cp: Int) {
        if (cp == 0x07) reset()
        // else: discard payload byte; ESC is handled globally in step().
    }

    private companion object {
        const val MAX_OSC_LENGTH = 4096
    }
}
