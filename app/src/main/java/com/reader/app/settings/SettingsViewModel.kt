package com.reader.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.app.data.repository.DEFAULT_TTS_SPEED
import com.reader.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val ttsSpeed: StateFlow<Float> = settingsRepository.ttsSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_TTS_SPEED)

    fun setTtsSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.setTtsSpeed(speed) }
    }
}
