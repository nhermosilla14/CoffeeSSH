package cl.segfault.coffeessh.terminal

/** Ring buffer of lines that have scrolled off the top of the main screen. */
class Scrollback(private val maxLines: Int) {
    private val buffer = ArrayDeque<Array<Cell>>()

    fun push(line: Array<Cell>) {
        if (buffer.size >= maxLines) buffer.removeFirst()
        buffer.addLast(line)
    }

    val size: Int get() = buffer.size

    /** Line at [index], where 0 is the oldest retained line. */
    fun lineFromTop(index: Int): Array<Cell> = buffer[index]

    fun clear() = buffer.clear()
}
