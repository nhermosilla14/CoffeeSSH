package cl.segfault.coffeessh.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.segfault.coffeessh.CoffeeSshApp
import cl.segfault.coffeessh.data.db.ConnectionWithRefs
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(connectionsRepo: ConnectionsRepository) : ViewModel() {

    val frequent: StateFlow<List<ConnectionWithRefs>> =
        connectionsRepo.observeFrequent(limit = 3)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CoffeeSshApp).container
                DashboardViewModel(container.connectionsRepository)
            }
        }
    }
}
