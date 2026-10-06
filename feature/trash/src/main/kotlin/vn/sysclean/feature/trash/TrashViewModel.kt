package vn.sysclean.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.sysclean.core.data.repository.TrashRepository
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.model.TrashItem
import javax.inject.Inject

data class TrashUiState(
    val loading: Boolean = true,
    /** A restore or delete is running; actions are disabled and a progress bar is shown. */
    val busy: Boolean = false,
    val items: List<TrashItem> = emptyList(),
    val retentionDays: Int = UserPreferencesRepository.DEFAULT_RETENTION_DAYS,
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

enum class TrashEvent { RESTORED, DELETED, EMPTIED }

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val events = Channel<TrashEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TrashEvent> = events.receiveAsFlow()

    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<TrashUiState> =
        combine(trashRepository.observe(), preferences.trashRetentionDays, busy) { items, days, working ->
            TrashUiState(loading = false, busy = working, items = items, retentionDays = days)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    init {
        viewModelScope.launch { trashRepository.purgeExpired() }
    }

    fun restore(item: TrashItem) = runBusy {
        if (trashRepository.restore(listOf(item.id)) > 0) events.send(TrashEvent.RESTORED)
    }

    fun delete(item: TrashItem) = runBusy {
        trashRepository.deletePermanently(listOf(item.id))
        events.send(TrashEvent.DELETED)
    }

    fun empty() = runBusy {
        trashRepository.empty()
        events.send(TrashEvent.EMPTIED)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }
}
