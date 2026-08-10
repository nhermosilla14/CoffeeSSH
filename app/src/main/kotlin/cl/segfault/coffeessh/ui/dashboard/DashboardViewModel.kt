package cl.segfault.coffeessh.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.db.ConnectionWithRefs
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import cl.segfault.coffeessh.ssh.SshSessionRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class ActiveSessionInfo(
    val sessionId: String,
    val connection: ConnectionWithRefs,
    val name: String?,
)

class DashboardViewModel(
    private val connectionsRepo: ConnectionsRepository,
    private val sessionRegistry: SshSessionRegistry,
) : ViewModel() {

    val frequent: StateFlow<List<ConnectionWithRefs>> =
        connectionsRepo.observeFrequent(limit = 3)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeSessions = MutableStateFlow<List<ActiveSessionInfo>>(emptyList())
    val activeSessions = _activeSessions.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRegistry.activeSessions.collect { sessions ->
                _activeSessions.value = sessions.filter {
                    it.session.state.value !is cl.segfault.coffeessh.ssh.SshSessionState.Disconnected &&
                        it.session.state.value !is cl.segfault.coffeessh.ssh.SshSessionState.Failed
                }.mapNotNull { active ->
                    connectionsRepo.get(active.connectionId)?.let { connection ->
                        ActiveSessionInfo(active.sessionId, connection, active.name)
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CoffeeSshApp).container
                DashboardViewModel(container.connectionsRepository, container.sshSessionRegistry)
            }
        }
    }
}
