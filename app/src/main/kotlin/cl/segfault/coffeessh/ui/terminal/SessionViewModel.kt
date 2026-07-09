package cl.segfault.coffeessh.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import cl.segfault.coffeessh.data.repo.IdentitiesRepository
import cl.segfault.coffeessh.ssh.SshForegroundService
import cl.segfault.coffeessh.ssh.SshSession
import cl.segfault.coffeessh.ssh.SshSessionRegistry
import cl.segfault.coffeessh.ssh.SshSessionState
import cl.segfault.coffeessh.terminal.Terminal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUiState(
    val title: String = "",
    val sshState: SshSessionState = SshSessionState.Idle,
    val missingIdentity: Boolean = false,
)

class SessionViewModel(
    application: Application,
    private val connectionId: Long,
    private val connectionsRepo: ConnectionsRepository,
    private val identitiesRepo: IdentitiesRepository,
    private val registry: SshSessionRegistry,
) : AndroidViewModel(application) {

    private val session: SshSession = registry.getOrCreate(connectionId)
    val terminal: Terminal get() = session.terminal

    private val _ui = MutableStateFlow(SessionUiState())
    val ui: StateFlow<SessionUiState> = _ui.asStateFlow()

    val sshState: StateFlow<SshSessionState> = session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), session.state.value)

    init {
        session.onConnected = {
            viewModelScope.launch { connectionsRepo.logConnected(connectionId) }
        }
        viewModelScope.launch {
            val withRefs = connectionsRepo.get(connectionId)
            val title = withRefs?.connection?.nickname ?: withRefs?.connection?.host ?: ""
            _ui.value = _ui.value.copy(title = title)

            val identityId = withRefs?.connection?.identityId
            val draft = identityId?.let { identitiesRepo.getDraft(it) }
            if (draft == null || (draft.password.isBlank() && draft.privateKey.isBlank())) {
                _ui.value = _ui.value.copy(missingIdentity = true)
                return@launch
            }

            SshForegroundService.start(application)
            session.start(
                host = withRefs.connection.host,
                port = withRefs.connection.port,
                username = draft.username,
                password = draft.password.ifBlank { null },
                privateKeyPem = draft.privateKey.ifBlank { null },
            )
        }
    }

    fun sendInput(bytes: ByteArray) = session.sendInput(bytes)

    fun resize(rows: Int, cols: Int) = session.resize(rows, cols)

    fun confirmHostKey(accept: Boolean) = session.confirmHostKey(accept)

    fun retry() {
        viewModelScope.launch {
            val withRefs = connectionsRepo.get(connectionId) ?: return@launch
            val identityId = withRefs.connection.identityId ?: return@launch
            val draft = identitiesRepo.getDraft(identityId) ?: return@launch
            SshForegroundService.start(getApplication())
            session.start(
                host = withRefs.connection.host,
                port = withRefs.connection.port,
                username = draft.username,
                password = draft.password.ifBlank { null },
                privateKeyPem = draft.privateKey.ifBlank { null },
            )
        }
    }

    /** Disconnects but keeps the session registered (e.g. explicit user action from the menu). */
    fun disconnect() = session.disconnect()

    companion object {
        fun factory(connectionId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CoffeeSshApp
                @Suppress("UNCHECKED_CAST")
                SessionViewModel(
                    application = app,
                    connectionId = connectionId,
                    connectionsRepo = app.container.connectionsRepository,
                    identitiesRepo = app.container.identitiesRepository,
                    registry = app.container.sshSessionRegistry,
                )
            }
        }
    }
}
