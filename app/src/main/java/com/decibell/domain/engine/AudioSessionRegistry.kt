package com.decibell.domain.engine

import com.decibell.data.model.ActiveAudioSession
import com.decibell.util.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class EngineStatus {
    data object Inactive : EngineStatus()
    data class ActiveSession(
        val sessionId: Int,
        val packageName: String,
        val bandCount: Int
    ) : EngineStatus()
    data class ActiveGlobalMix(val bandCount: Int) : EngineStatus()
    data class Error(val message: String) : EngineStatus()
}

object AudioSessionRegistry {
    private val _activeSessions = MutableStateFlow<List<ActiveAudioSession>>(emptyList())
    val activeSessions: StateFlow<List<ActiveAudioSession>> = _activeSessions.asStateFlow()

    private val _engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Inactive)
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    private val _spectrumBins = MutableStateFlow(FloatArray(AppConstants.VISUALIZER_BINS) { 0f })
    val spectrumBins: StateFlow<FloatArray> = _spectrumBins.asStateFlow()

    fun onSessionOpened(sessionId: Int, packageName: String) {
        if (sessionId <= 0) return
        val resolvedPkg = packageName.ifBlank { "media.player" }
        _activeSessions.update { current ->
            val filtered = current.filterNot { it.sessionId == sessionId }
            listOf(
                ActiveAudioSession(
                    sessionId = sessionId,
                    packageName = resolvedPkg,
                    openedAtMillis = System.currentTimeMillis()
                )
            ) + filtered
        }
    }

    fun onSessionClosed(sessionId: Int) {
        if (sessionId <= 0) return
        _activeSessions.update { current ->
            current.filterNot { it.sessionId == sessionId }
        }
    }

    fun updateEngineStatus(status: EngineStatus) {
        _engineStatus.value = status
    }

    fun updateSpectrum(bins: FloatArray) {
        _spectrumBins.value = bins
    }

    fun clearSpectrum() {
        _spectrumBins.value = FloatArray(AppConstants.VISUALIZER_BINS) { 0f }
    }
}
