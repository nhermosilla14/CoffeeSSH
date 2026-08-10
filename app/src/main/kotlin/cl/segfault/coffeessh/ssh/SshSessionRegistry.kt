package cl.segfault.coffeessh.ssh

import cl.segfault.coffeessh.data.db.KnownHostDao
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveSession(
    val sessionId: String,
    val connectionId: Long,
    val session: SshSession,
    val name: String? = null,
)

/**
 * Holds the app's active [SshSession]s keyed by connection id, shared between the UI
 * (which creates/observes sessions) and [SshForegroundService] (which keeps them alive
 * while the app is backgrounded and reports how many are active in its notification).
 */
class SshSessionRegistry(private val knownHostDao: KnownHostDao) {
    private val sessions = ConcurrentHashMap<String, ActiveSession>()
    private val _activeSessions = MutableStateFlow<List<ActiveSession>>(emptyList())
    val activeSessions: StateFlow<List<ActiveSession>> = _activeSessions.asStateFlow()

    fun create(connectionId: Long): ActiveSession {
        val id = java.util.UUID.randomUUID().toString()
        val active = ActiveSession(id, connectionId, SshSession(connectionId, knownHostDao))
        sessions[id] = active
        publish()
        return active
    }

    fun get(sessionId: String): ActiveSession? = sessions[sessionId]

    fun rename(sessionId: String, name: String?) {
        sessions.computeIfPresent(sessionId) { _, active -> active.copy(name = name?.trim()?.ifBlank { null }) }
        publish()
    }

    fun remove(sessionId: String) {
        sessions.remove(sessionId)?.session?.destroy()
        publish()
    }

    fun all(): Collection<SshSession> = sessions.values.map { it.session }

    fun connectedCount(): Int = sessions.values.count { it.session.state.value == SshSessionState.Connected }

    private fun publish() {
        _activeSessions.value = sessions.values.toList()
    }
}
