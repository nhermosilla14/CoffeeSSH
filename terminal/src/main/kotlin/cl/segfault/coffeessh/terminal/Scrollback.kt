package cl.segfault.coffeessh.terminal

/** Ring buffer of lines that have scrolled off the top of the main screen. */
class Scrollback(private val maxLines: Int) {
    private val buffer = ArrayDeque<Array<Cell>>()

    @Synchronized
    fun push(line: Array<Cell>) {
        if (buffer.size >= maxLines) buffer.removeFirst()
        buffer.addLast(line)
    }

    @get:Synchronized
    val size: Int get() = buffer.size

    /** Line at [index], where 0 is the oldest retained line. */
    @Synchronized
    fun lineFromTop(index: Int): Array<Cell> = buffer[index]

    @Synchronized
    fun snapshot(): List<Array<Cell>> = buffer.map { it.copyOf() }

    @Synchronized
    fun clear() = buffer.clear()
}
