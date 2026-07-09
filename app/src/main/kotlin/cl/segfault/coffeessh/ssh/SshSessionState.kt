package cl.segfault.coffeessh.ssh

/** Lifecycle state of one [SshSession]. */
sealed interface SshSessionState {
    data object Idle : SshSessionState
    data object Connecting : SshSessionState

    /**
     * The remote host key needs a decision before the connection can proceed (TOFU).
     * [isChanged] is true when this fingerprint contradicts a *different* one already
     * stored for this host - a possible MITM, surfaced as a much louder warning in the UI.
     */
    data class AwaitingHostKeyConfirmation(
        val host: String,
        val port: Int,
        val keyType: String,
        val fingerprint: String,
        val isChanged: Boolean,
    ) : SshSessionState

    data object Authenticating : SshSessionState
    data object Connected : SshSessionState
    data class Failed(val message: String) : SshSessionState
    data object Disconnected : SshSessionState
}
