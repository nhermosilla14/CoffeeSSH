package cl.segfault.coffeessh.ui.identities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.repo.IdentitiesRepository
import cl.segfault.coffeessh.data.repo.IdentityDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdentityEditorState(
    val isNew: Boolean = true,
    val nickname: String = "",
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = nickname.isNotBlank() && username.isNotBlank()
}

class IdentityEditorViewModel(
    private val identitiesRepo: IdentitiesRepository,
    private val identityId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(IdentityEditorState(isNew = identityId == null))
    val state: StateFlow<IdentityEditorState> = _state.asStateFlow()

    init {
        if (identityId != null) {
            viewModelScope.launch {
                identitiesRepo.getDraft(identityId)?.let { draft ->
                    _state.update {
                        it.copy(
                            isNew = false,
                            nickname = draft.nickname,
                            username = draft.username,
                            password = draft.password,
                            privateKey = draft.privateKey,
                        )
                    }
                }
            }
        }
    }

    fun onNicknameChange(value: String) = _state.update { it.copy(nickname = value) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onPrivateKeyChange(value: String) = _state.update { it.copy(privateKey = value) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            identitiesRepo.save(
                IdentityDraft(
                    id = identityId,
                    nickname = s.nickname,
                    username = s.username,
                    password = s.password,
                    privateKey = s.privateKey,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CoffeeSshApp).container
                val handle: SavedStateHandle = createSavedStateHandle()
                IdentityEditorViewModel(
                    identitiesRepo = container.identitiesRepository,
                    identityId = handle.get<Long>("identityId"),
                )
            }
        }
    }
}
