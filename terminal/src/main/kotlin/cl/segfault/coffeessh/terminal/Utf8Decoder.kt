package cl.segfault.coffeessh.terminal

/**
 * Incremental UTF-8 decoder. Network reads can split a multi-byte sequence across two
 * `write()` calls, so partial state (the bytes seen so far of the current sequence) is
 * kept between [decode] calls.
 *
 * Invalid sequences are replaced with U+FFFD, one replacement per offending byte, so the
 * parser never gets stuck waiting for continuation bytes that will never arrive.
 */
class Utf8Decoder {
    private var need = 0
    private var codePoint = 0
    private var minCodePoint = 0

    fun decode(bytes: ByteArray, onCodePoint: (Int) -> Unit) {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (need == 0) {
                when {
                    b < 0x80 -> onCodePoint(b)
                    b and 0xE0 == 0xC0 -> start(b and 0x1F, 1, 0x80)
                    b and 0xF0 == 0xE0 -> start(b and 0x0F, 2, 0x800)
                    b and 0xF8 == 0xF0 -> start(b and 0x07, 3, 0x10000)
                    else -> onCodePoint(0xFFFD) // stray continuation/invalid lead byte
                }
            } else if (b and 0xC0 == 0x80) {
                codePoint = (codePoint shl 6) or (b and 0x3F)
                need--
                if (need == 0) onCodePoint(if (codePoint < minCodePoint) 0xFFFD else codePoint)
            } else {
                // Sequence aborted by a non-continuation byte; emit replacement and
                // reprocess this same byte as a fresh sequence start.
                need = 0
                onCodePoint(0xFFFD)
                continue
            }
            i++
        }
    }

    private fun start(initial: Int, remaining: Int, min: Int) {
        codePoint = initial
        need = remaining
        minCodePoint = min
    }
}
