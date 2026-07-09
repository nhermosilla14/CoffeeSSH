package cl.segfault.coffeessh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8DecoderTest {

    @Test
    fun decodesAsciiOneToOne() {
        val out = mutableListOf<Int>()
        Utf8Decoder().decode("Hi".encodeToByteArray()) { out.add(it) }
        assertEquals(listOf('H'.code, 'i'.code), out)
    }

    @Test
    fun decodesTwoByteSequenceInOneChunk() {
        val out = mutableListOf<Int>()
        Utf8Decoder().decode("é".encodeToByteArray()) { out.add(it) } // U+00E9, 2 bytes
        assertEquals(listOf(0xE9), out)
    }

    @Test
    fun decodesThreeByteSequenceSplitAcrossChunks() {
        val bytes = "€".encodeToByteArray() // U+20AC, 3 bytes: E2 82 AC
        assertEquals(3, bytes.size)
        val out = mutableListOf<Int>()
        val decoder = Utf8Decoder()
        decoder.decode(bytes.copyOfRange(0, 1)) { out.add(it) }
        decoder.decode(bytes.copyOfRange(1, 2)) { out.add(it) }
        assertEquals(emptyList(), out) // nothing emitted yet, sequence incomplete
        decoder.decode(bytes.copyOfRange(2, 3)) { out.add(it) }
        assertEquals(listOf(0x20AC), out)
    }

    @Test
    fun decodesFourByteAstralSequence() {
        val out = mutableListOf<Int>()
        Utf8Decoder().decode("\uD83D\uDE00".toByteArray(Charsets.UTF_8)) { out.add(it) } // 😀 U+1F600
        assertEquals(listOf(0x1F600), out)
    }

    @Test
    fun invalidContinuationByteYieldsReplacementAndResyncs() {
        val out = mutableListOf<Int>()
        // 0xC3 starts a 2-byte sequence but is followed by an invalid continuation, then 'A'.
        Utf8Decoder().decode(byteArrayOf(0xC3.toByte(), 'A'.code.toByte())) { out.add(it) }
        assertEquals(listOf(0xFFFD, 'A'.code), out)
    }

    @Test
    fun strayContinuationByteAloneYieldsReplacement() {
        val out = mutableListOf<Int>()
        Utf8Decoder().decode(byteArrayOf(0x80.toByte())) { out.add(it) }
        assertEquals(listOf(0xFFFD), out)
    }
}
