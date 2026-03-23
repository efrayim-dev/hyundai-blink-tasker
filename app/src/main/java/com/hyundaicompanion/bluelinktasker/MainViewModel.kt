package com.hyundaicompanion.bluelinktasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: BlueLinkRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events

    fun unlock() = runCommand { repository.unlock() }
    fun lock() = runCommand { repository.lock() }
    fun remoteStart(options: RemoteStartOptions) = runCommand { repository.remoteStart(options) }
    fun remoteStop() = runCommand { repository.remoteStop() }

    private fun runCommand(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            _events.emit(UiEvent.Busy(true))
            val result = block()
            _events.emit(UiEvent.Busy(false))
            result.fold(
                onSuccess = { _events.emit(UiEvent.Message(it)) },
                onFailure = { _events.emit(UiEvent.Error(it.message ?: it.toString())) },
            )
        }
    }

    sealed class UiEvent {
        data class Busy(val on: Boolean) : UiEvent()
        data class Message(val text: String) : UiEvent()
        data class Error(val text: String) : UiEvent()
    }

    class Factory(
        private val repository: BlueLinkRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(repository) as T
    }
}
