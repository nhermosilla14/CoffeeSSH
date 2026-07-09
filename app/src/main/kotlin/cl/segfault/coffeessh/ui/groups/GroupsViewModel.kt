package cl.segfault.coffeessh.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.db.GroupEntity
import cl.segfault.coffeessh.data.repo.GroupsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(private val groupsRepo: GroupsRepository) : ViewModel() {

    val groups: StateFlow<List<GroupEntity>> =
        groupsRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { groupsRepo.create(name) }
    }

    fun rename(group: GroupEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { groupsRepo.rename(group, newName) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { groupsRepo.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CoffeeSshApp).container
                GroupsViewModel(container.groupsRepository)
            }
        }
    }
}
