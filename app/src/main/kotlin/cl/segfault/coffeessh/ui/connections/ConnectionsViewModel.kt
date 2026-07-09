package cl.segfault.coffeessh.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.db.ConnectionWithRefs
import cl.segfault.coffeessh.data.db.GroupEntity
import cl.segfault.coffeessh.data.db.IdentityEntity
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import cl.segfault.coffeessh.data.repo.GroupsRepository
import cl.segfault.coffeessh.data.repo.IdentitiesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One collapsible section of the connections list. [group] == null means "ungrouped". */
data class GroupSection(
    val group: GroupEntity?,
    val connections: List<ConnectionWithRefs>,
)

class ConnectionsViewModel(
    private val connectionsRepo: ConnectionsRepository,
    private val identitiesRepo: IdentitiesRepository,
    groupsRepo: GroupsRepository,
) : ViewModel() {

    val sections: StateFlow<List<GroupSection>> =
        combine(connectionsRepo.observeAll(), groupsRepo.observeAll()) { connections, groups ->
            buildSections(connections, groups)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val identities: StateFlow<List<IdentityEntity>> =
        identitiesRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteConnection(id: Long) {
        viewModelScope.launch { connectionsRepo.delete(id) }
    }

    fun duplicateConnection(id: Long) {
        viewModelScope.launch { connectionsRepo.duplicate(id) }
    }

    fun deleteIdentity(id: Long) {
        viewModelScope.launch { identitiesRepo.delete(id) }
    }

    private fun buildSections(
        connections: List<ConnectionWithRefs>,
        groups: List<GroupEntity>,
    ): List<GroupSection> {
        val grouped = groups.map { group ->
            GroupSection(
                group = group,
                connections = connections.filter { c -> c.groups.any { it.id == group.id } },
            )
        }
        val ungrouped = connections.filter { it.groups.isEmpty() }
        return if (ungrouped.isEmpty()) grouped else grouped + GroupSection(null, ungrouped)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CoffeeSshApp).container
                ConnectionsViewModel(
                    connectionsRepo = container.connectionsRepository,
                    identitiesRepo = container.identitiesRepository,
                    groupsRepo = container.groupsRepository,
                )
            }
        }
    }
}
