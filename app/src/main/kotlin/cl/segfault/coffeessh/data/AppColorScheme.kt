package cl.segfault.coffeessh.data

enum class AppColorScheme(val id: String) {
    COFFEE("coffee"),
    OCEAN("ocean"),
    FOREST("forest"),
    AMBER("amber"),
    VIOLET("violet");

    companion object {
        fun fromId(id: String): AppColorScheme = entries.firstOrNull { it.id == id } ?: COFFEE
    }
}
