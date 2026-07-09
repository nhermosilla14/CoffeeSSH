package cl.segfault.coffeessh.ui.connections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.db.ConnectionEntity
import cl.segfault.coffeessh.data.db.GroupEntity
import cl.segfault.coffeessh.data.db.IdentityEntity
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import cl.segfault.coffeessh.data.repo.GroupsRepository
import cl.segfault.coffeessh.data.repo.IdentitiesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionEditorState(
    val isNew: Boolean = true,
    val nickname: String = "",
    val host: String = "",
    val port: String = "22",
    val identityId: Long? = null,
    val selectedGroupIds: Set<Long> = emptySet(),
    val identities: List<IdentityEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val saved: Boolean = false,
) {
    val portValid: Boolean get() = (port.toIntOrNull() ?: 0) in 1..65535
    val canSave: Boolean get() = host.isNotBlank() && portValid
}

class ConnectionEditorViewModel(
    private val connectionsRepo: ConnectionsRepository,
    private val groupsRepo: GroupsRepository,
    identitiesRepo: IdentitiesRepository,
    private val connectionId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionEditorState(isNew = connectionId == null))
    val state: StateFlow<ConnectionEditorState> = _state.asStateFlow()

    /** Loaded original (when editing) so createdAt/sortOrder are preserved on save. */
    private var original: ConnectionEntity? = null

    init {
        viewModelScope.launch {
            identitiesRepo.observeAll().collect { list ->
                _state.update { it.copy(identities = list) }
            }
        }
        viewModelScope.launch {
            groupsRepo.observeAll().collect { list ->
                _state.update { it.copy(groups = list) }
            }
        }
        if (connectionId != null) {
            viewModelScope.launch {
                connectionsRepo.get(connectionId)?.let { existing ->
                    original = existing.connection
                    _state.update {
                        it.copy(
                            isNew = false,
                            nickname = existing.connection.nickname.orEmpty(),
                            host = existing.connection.host,
                            port = existing.connection.port.toString(),
                            identityId = existing.connection.identityId,
                            selectedGroupIds = existing.groups.map { g -> g.id }.toSet(),
                        )
                    }
                }
            }
        }
    }

    fun onNicknameChange(value: String) = _state.update { it.copy(nickname = value) }
    fun onHostChange(value: String) = _state.update { it.copy(host = value) }
    fun onPortChange(value: String) = _state.update { it.copy(port = value.filter(Char::isDigit).take(5)) }
    fun onIdentityChange(id: Long?) = _state.update { it.copy(identityId = id) }

    fun toggleGroup(id: Long) = _state.update {
        val selected = it.selectedGroupIds
        it.copy(selectedGroupIds = if (id in selected) selected - id else selected + id)
    }

    fun createGroupAndSelect(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = groupsRepo.create(name)
            _state.update { it.copy(selectedGroupIds = it.selectedGroupIds + id) }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val entity = (original ?: ConnectionEntity(host = "")).copy(
                nickname = s.nickname.trim().ifEmpty { null },
                host = s.host.trim(),
                port = s.port.toInt(),
                identityId = s.identityId,
            )
            connectionsRepo.save(entity, s.selectedGroupIds.toList())
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CoffeeSshApp).container
                val handle: SavedStateHandle = createSavedStateHandle()
                ConnectionEditorViewModel(
                    connectionsRepo = container.connectionsRepository,
                    groupsRepo = container.groupsRepository,
                    identitiesRepo = container.identitiesRepository,
                    connectionId = handle.get<Long>("connectionId"),
                )
            }
        }
    }
}
