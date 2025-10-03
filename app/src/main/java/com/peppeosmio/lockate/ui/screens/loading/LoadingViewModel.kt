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

    init {
    }
}
