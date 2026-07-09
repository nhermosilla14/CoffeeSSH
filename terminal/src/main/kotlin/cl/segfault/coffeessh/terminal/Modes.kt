package cl.segfault.coffeessh.terminal

/** Terminal mode flags, toggled via CSI `h`/`l` (DEC private with `?` prefix, or ANSI). */
class Modes internal constructor() {
    /** DECAWM (mode 7): wrap to the next line when printing past the last column. */
    var autowrap: Boolean = true
        internal set

    /** DECOM (mode 6): cursor addressing is relative to the scroll region. */
    var originMode: Boolean = false
        internal set

    /** IRM (ANSI mode 4): typed/printed characters push existing ones to the right. */
    var insertMode: Boolean = false
        internal set

    /** DECTCEM (mode 25): whether the cursor should be drawn at all. */
    var cursorVisible: Boolean = true
        internal set

    /** DECCKM (mode 1): arrow keys send `SS3` sequences instead of `CSI` ones. */
    var applicationCursorKeys: Boolean = false
        internal set

    /** DECPAM/DECPNM: numeric keypad sends application sequences instead of digits. */
    var applicationKeypad: Boolean = false
        internal set

    /** Mode 2004: pasted text is wrapped in `ESC[200~ ... ESC[201~`. */
    var bracketedPaste: Boolean = false
        internal set

    /** Mode 1049/1047/47: whether the alternate screen buffer is currently shown. */
    var altScreenActive: Boolean = false
        internal set

    /** DECSCNM (mode 5): swap default foreground/background across the whole screen. */
    var reverseVideo: Boolean = false
        internal set
}
