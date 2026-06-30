package com.wgs.app.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wgs.app.data.repository.ScanRepository
import com.wgs.app.service.ScanPhase
import com.wgs.app.service.ScanStateManager
import com.wgs.app.service.ScreenCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val scanStateManager: ScanStateManager,
    private val scanRepository: ScanRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val WAR_ID_KEY = stringPreferencesKey("war_id")
    }

    val scanPhase: StateFlow<ScanPhase> = scanStateManager.phase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanPhase.Idle)

    val warId: Flow<String> = dataStore.data.map { prefs ->
        prefs[WAR_ID_KEY] ?: defaultWarId()
    }

    private fun defaultWarId(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        return "war_${sdf.format(java.util.Date())}"
    }

    fun setWarId(id: String) {
        viewModelScope.launch {
            dataStore.edit { it[WAR_ID_KEY] = id }
        }
    }

    fun dismissDuplicate(accept: Boolean, captureService: ScreenCaptureService?) {
        val phase = scanPhase.value
        if (phase is ScanPhase.Duplicate) {
            if (accept) {
                captureService?.confirmSave(phase.baseNumber, phase.playerName, phase.gold)
            } else {
                scanStateManager.resetToWaiting()
            }
        }
    }

    fun retryAfterError() {
        scanStateManager.retryFromError()
    }
}
