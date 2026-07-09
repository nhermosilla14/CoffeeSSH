package cl.segfault.coffeessh.ssh

import cl.segfault.coffeessh.data.db.KnownHostDao
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the app's active [SshSession]s keyed by connection id, shared between the UI
 * (which creates/observes sessions) and [SshForegroundService] (which keeps them alive
 * while the app is backgrounded and reports how many are active in its notification).
 */
class SshSessionRegistry(private val knownHostDao: KnownHostDao) {
    private val sessions = ConcurrentHashMap<Long, SshSession>()

    fun getOrCreate(connectionId: Long): SshSession =
        sessions.computeIfAbsent(connectionId) { SshSession(it, knownHostDao) }

    fun get(connectionId: Long): SshSession? = sessions[connectionId]

    fun remove(connectionId: Long) {
        sessions.remove(connectionId)?.destroy()
    }

    fun all(): Collection<SshSession> = sessions.values

    fun connectedCount(): Int = sessions.values.count { it.state.value == SshSessionState.Connected }
}
