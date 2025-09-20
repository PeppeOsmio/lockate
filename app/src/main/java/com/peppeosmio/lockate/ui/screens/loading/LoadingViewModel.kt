package com.peppeosmio.lockate.ui.screens.loading
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peppeosmio.lockate.exceptions.ConnectionSettingsNotFoundException
import com.peppeosmio.lockate.service.ConnectionSettingsService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LoadingViewModel(
    private val connectionSettingsService: ConnectionSettingsService
) : ViewModel() {

    private val _selectedConnectionSettingsId = Channel<Long?>()
    val selectedConnectionSettingsId = _selectedConnectionSettingsId.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                val selectedConnectionSettings = connectionSettingsService.getSelectedConnectionSettings()
                _selectedConnectionSettingsId.send(selectedConnectionSettings.id!!)
            } catch (e: ConnectionSettingsNotFoundException) {
                _selectedConnectionSettingsId.send(null)
            }
        }
    }
}
