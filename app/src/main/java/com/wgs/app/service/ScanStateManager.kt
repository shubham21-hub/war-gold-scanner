package com.wgs.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class ScanPhase {
    object Idle : ScanPhase()
    object WaitingForBase : ScanPhase()
    data class BaseCapturing(val progress: String = "Scanning...") : ScanPhase()
    data class WaitingForGold(val baseNumber: Int, val playerName: String) : ScanPhase()
    data class GoldCapturing(val baseNumber: Int, val playerName: String) : ScanPhase()
    data class Saving(val baseNumber: Int, val playerName: String, val gold: Long) : ScanPhase()
    data class Duplicate(val baseNumber: Int, val playerName: String, val gold: Long) : ScanPhase()
    data class Success(val baseNumber: Int, val playerName: String, val gold: Long) : ScanPhase()
    data class Failure(val message: String, val phase: ScanPhase) : ScanPhase()
}

@Singleton
class ScanStateManager @Inject constructor() {

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val phase: StateFlow<ScanPhase> = _phase.asStateFlow()

    fun startSession() {
        _phase.value = ScanPhase.WaitingForBase
    }

    fun stopSession() {
        _phase.value = ScanPhase.Idle
    }

    fun setCapturingBase() {
        _phase.value = ScanPhase.BaseCapturing()
    }

    fun baseScanned(baseNumber: Int, playerName: String) {
        _phase.value = ScanPhase.WaitingForGold(baseNumber, playerName)
    }

    fun setCapturingGold(baseNumber: Int, playerName: String) {
        _phase.value = ScanPhase.GoldCapturing(baseNumber, playerName)
    }

    fun goldScanned(baseNumber: Int, playerName: String, gold: Long) {
        _phase.value = ScanPhase.Saving(baseNumber, playerName, gold)
    }

    fun duplicateDetected(baseNumber: Int, playerName: String, gold: Long) {
        _phase.value = ScanPhase.Duplicate(baseNumber, playerName, gold)
    }

    fun recordSaved(baseNumber: Int, playerName: String, gold: Long) {
        _phase.value = ScanPhase.Success(baseNumber, playerName, gold)
    }

    fun resetToWaiting() {
        _phase.value = ScanPhase.WaitingForBase
    }

    fun setError(message: String) {
        val previousPhase = when (val p = _phase.value) {
            is ScanPhase.BaseCapturing -> ScanPhase.WaitingForBase
            is ScanPhase.GoldCapturing -> ScanPhase.WaitingForGold(p.baseNumber, p.playerName)
            else -> ScanPhase.WaitingForBase
        }
        _phase.value = ScanPhase.Failure(message, previousPhase)
    }

    fun retryFromError() {
        val current = _phase.value
        if (current is ScanPhase.Failure) {
            _phase.value = current.phase
        }
    }
}
