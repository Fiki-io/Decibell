package com.decibell.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.decibell.R
import com.decibell.data.local.DecibellDatabase
import com.decibell.data.local.EqConfigEntity
import com.decibell.data.local.EqPresetEntity
import com.decibell.data.model.ActiveAudioSession
import com.decibell.data.model.BuiltInPresets
import com.decibell.data.model.EqualizerState
import com.decibell.data.model.LimiterSettings
import com.decibell.data.model.MbcPresetType
import com.decibell.data.model.ReverbPresetType
import com.decibell.data.model.SliderSize
import com.decibell.data.model.StandardBandConfigs
import com.decibell.data.repository.EqPreferencesRepository
import com.decibell.domain.engine.AudioSessionRegistry
import com.decibell.domain.engine.DynamicsAudioEngine
import com.decibell.domain.engine.EngineStatus
import com.decibell.service.AudioEffectService
import com.decibell.util.AppConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiFeedbackEvent {
    data class MessageRes(val resId: Int, val formatArgs: List<Any> = emptyList()) : UiFeedbackEvent()
}

enum class InfoTopic(val titleRes: Int, val descRes: Int) {
    SIZE(R.string.info_title_size, R.string.info_desc_size),
    VISUALIZER(R.string.info_title_visualizer, R.string.info_desc_visualizer),
    BASS_BOOST(R.string.info_title_bass_boost, R.string.info_desc_bass_boost),
    SOUND_3D(R.string.info_title_3d_sound, R.string.info_desc_3d_sound),
    AUTO_PAN(R.string.info_title_auto_pan, R.string.info_desc_auto_pan),
    MBC(R.string.info_title_mbc, R.string.info_desc_mbc),
    LIMITER(R.string.info_title_limiter, R.string.info_desc_limiter),
    EQ_CONFIG(R.string.info_title_eq_config, R.string.info_desc_eq_config)
}

data class EqualizerUiState(
    val eqState: EqualizerState = EqualizerState(),
    val customPresets: List<EqPresetEntity> = emptyList(),
    val customConfigs: List<EqConfigEntity> = emptyList(),
    val activeSessions: List<ActiveAudioSession> = emptyList(),
    val engineStatus: EngineStatus = EngineStatus.Inactive,
    val isCurrentPresetModified: Boolean = false
)

private data class LiveGainsOverride(
    val bandCount: Int,
    val leftGainsDb: List<Float>,
    val rightGainsDb: List<Float>,
    val activePresetName: String
)

class EqualizerViewModel(
    application: Application,
    private val repository: EqPreferencesRepository,
    private val audioEngine: DynamicsAudioEngine
) : AndroidViewModel(application) {

    private val audioManager =
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _systemVolumePercent = MutableStateFlow(readSystemVolumePercent())
    private val _liveGainsOverride = MutableStateFlow<LiveGainsOverride?>(null)
    private var gainSaveJob: Job? = null

    private val _feedbackEvents = MutableSharedFlow<UiFeedbackEvent>(extraBufferCapacity = 8)
    val feedbackEvents: SharedFlow<UiFeedbackEvent> = _feedbackEvents.asSharedFlow()

    val spectrumBins: StateFlow<FloatArray> = AudioSessionRegistry.spectrumBins

    private val effectiveEqStateFlow = combine(
        repository.stateFlow,
        _liveGainsOverride
    ) { repoState, liveOverride ->
        if (liveOverride != null && liveOverride.bandCount == repoState.bandCount) {
            repoState.copy(
                leftGainsDb = liveOverride.leftGainsDb,
                rightGainsDb = liveOverride.rightGainsDb,
                activePresetName = liveOverride.activePresetName
            )
        } else {
            repoState
        }
    }

    val uiState: StateFlow<EqualizerUiState> = combine(
        effectiveEqStateFlow,
        repository.customPresetsFlow,
        repository.customConfigsFlow,
        AudioSessionRegistry.activeSessions,
        combine(AudioSessionRegistry.engineStatus, _systemVolumePercent) { status, vol -> status to vol }
    ) { eqState, presets, configs, sessions, (engineStatus, volumePct) ->
        val stateWithVolume = eqState.copy(outputVolumePercent = volumePct)
        val isModified = eqState.activePresetName == AppConstants.CUSTOM_PRESET_KEY
        EqualizerUiState(
            eqState = stateWithVolume,
            customPresets = presets,
            customConfigs = configs,
            activeSessions = sessions,
            engineStatus = engineStatus,
            isCurrentPresetModified = isModified
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EqualizerUiState(
            eqState = EqualizerState(outputVolumePercent = _systemVolumePercent.value)
        )
    )

    init {
        var previousEqEnabled: Boolean? = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            combine(
                effectiveEqStateFlow,
                AudioSessionRegistry.activeSessions
            ) { state, sessions ->
                state to sessions
            }.collect { (state, sessions) ->
                audioEngine.applyState(state, sessions)
                if (previousEqEnabled != state.eqEnabled) {
                    previousEqEnabled = state.eqEnabled
                    AudioEffectService.syncServiceState(getApplication(), state.eqEnabled)
                }
            }
        }
    }

    fun onExternalControlPanelSession(sessionId: Int, packageName: String) {
        if (sessionId > 0) {
            AudioSessionRegistry.onSessionOpened(sessionId, packageName)
        }
    }

    fun refreshSystemVolume() {
        _systemVolumePercent.value = readSystemVolumePercent()
    }

    fun setEqEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEqEnabled(enabled)
        }
    }

    fun selectStandardBandCount(bandCount: Int) {
        if (bandCount !in AppConstants.ALL_CONFIG_BAND_COUNTS) return
        val current = uiState.value.eqState
        gainSaveJob?.cancel()
        _liveGainsOverride.value = null
        viewModelScope.launch {
            repository.setBandConfigStandard(bandCount, current.leftGainsDb, current.rightGainsDb)
        }
    }

    fun selectCustomConfig(config: EqConfigEntity) {
        val current = uiState.value.eqState
        gainSaveJob?.cancel()
        _liveGainsOverride.value = null
        viewModelScope.launch {
            repository.setCustomBandConfig(config, current.leftGainsDb, current.rightGainsDb)
        }
    }

    fun setChannelJoined(joined: Boolean) {
        val current = uiState.value.eqState
        gainSaveJob?.cancel()
        if (joined) {
            _liveGainsOverride.value = LiveGainsOverride(
                bandCount = current.bandCount,
                leftGainsDb = current.leftGainsDb,
                rightGainsDb = current.leftGainsDb,
                activePresetName = current.activePresetName
            )
        }
        viewModelScope.launch {
            repository.setChannelJoined(joined, current.leftGainsDb)
            _liveGainsOverride.value = null
        }
    }

    fun updateBandGain(isLeftChannel: Boolean, bandIndex: Int, newGainDb: Float) {
        val current = uiState.value.eqState
        if (bandIndex !in 0 until current.bandCount) return
        val maxRange = current.maxGainRangeDb
        val roundedGain = ((newGainDb.coerceIn(-maxRange, maxRange) * 10f).roundToInt()) / 10f

        val baseLeft = _liveGainsOverride.value
            ?.takeIf { it.bandCount == current.bandCount }
            ?.leftGainsDb
            ?: current.leftGainsDb
        val baseRight = _liveGainsOverride.value
            ?.takeIf { it.bandCount == current.bandCount }
            ?.rightGainsDb
            ?: current.rightGainsDb

        val updatedLeft = baseLeft.toMutableList()
        val updatedRight = baseRight.toMutableList()

        if (current.isChannelJoined) {
            updatedLeft[bandIndex] = roundedGain
            updatedRight[bandIndex] = roundedGain
        } else if (isLeftChannel) {
            updatedLeft[bandIndex] = roundedGain
        } else {
            updatedRight[bandIndex] = roundedGain
        }

        _liveGainsOverride.value = LiveGainsOverride(
            bandCount = current.bandCount,
            leftGainsDb = updatedLeft,
            rightGainsDb = updatedRight,
            activePresetName = AppConstants.CUSTOM_PRESET_KEY
        )

        gainSaveJob?.cancel()
        gainSaveJob = viewModelScope.launch {
            delay(80L)
            repository.updateGains(
                leftGains = updatedLeft,
                rightGains = updatedRight,
                presetName = AppConstants.CUSTOM_PRESET_KEY
            )
        }
    }

    fun setMaxGainRangeDb(requestedMaxDb: Int) {
        if (requestedMaxDb < AppConstants.MIN_CUSTOM_MAX_GAIN_DB.toInt() ||
            requestedMaxDb > AppConstants.MAX_CUSTOM_MAX_GAIN_DB.toInt()
        ) {
            emitFeedback(R.string.err_invalid_db_range)
            return
        }
        val current = uiState.value.eqState
        val newRange = requestedMaxDb.toFloat()
        val clampedLeft = current.leftGainsDb.map { it.coerceIn(-newRange, newRange) }
        val clampedRight = current.rightGainsDb.map { it.coerceIn(-newRange, newRange) }
        gainSaveJob?.cancel()
        _liveGainsOverride.value = LiveGainsOverride(
            bandCount = current.bandCount,
            leftGainsDb = clampedLeft,
            rightGainsDb = clampedRight,
            activePresetName = current.activePresetName
        )
        viewModelScope.launch {
            repository.setMaxGainRangeDb(
                maxRangeDb = newRange,
                currentLeft = clampedLeft,
                currentRight = clampedRight,
                currentPreGain = current.preGainDb
            )
            _liveGainsOverride.value = null
            emitFeedback(R.string.msg_db_range_updated, listOf(requestedMaxDb))
        }
    }

    fun selectBuiltInPreset(presetName: String) {
        val current = uiState.value.eqState
        val gains = BuiltInPresets.gainsForPreset(presetName, current.bandCount)
        gainSaveJob?.cancel()
        _liveGainsOverride.value = LiveGainsOverride(
            bandCount = current.bandCount,
            leftGainsDb = gains,
            rightGainsDb = gains,
            activePresetName = presetName
        )
        viewModelScope.launch {
            repository.updateGains(
                leftGains = gains,
                rightGains = gains,
                presetName = presetName
            )
            _liveGainsOverride.value = null
        }
    }

    fun selectCustomPreset(preset: EqPresetEntity) {
        val current = uiState.value.eqState
        val left = EqPreferencesRepository.parseGainsCsv(preset.leftGainsCsv, current.bandCount)
        val right = if (preset.isChannelJoined) {
            left
        } else {
            EqPreferencesRepository.parseGainsCsv(preset.rightGainsCsv, current.bandCount)
        }
        gainSaveJob?.cancel()
        _liveGainsOverride.value = LiveGainsOverride(
            bandCount = current.bandCount,
            leftGainsDb = left,
            rightGainsDb = right,
            activePresetName = preset.name
        )
        viewModelScope.launch {
            repository.setChannelJoined(preset.isChannelJoined, left)
            repository.setPreGainDb(preset.preGainDb)
            repository.setBalanceDb(preset.balanceDb)
            repository.updateGains(
                leftGains = left,
                rightGains = right,
                presetName = preset.name
            )
            _liveGainsOverride.value = null
        }
    }

    fun saveCustomPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitFeedback(R.string.err_invalid_name)
            return
        }
        val snapshot = uiState.value.eqState
        viewModelScope.launch {
            val result = repository.saveCustomPresetVerified(trimmed, snapshot)
            result.onSuccess { saved ->
                emitFeedback(R.string.msg_preset_saved, listOf(saved.name))
            }.onFailure {
                emitFeedback(R.string.err_invalid_name)
            }
        }
    }

    fun deleteCustomPreset(presetName: String) {
        viewModelScope.launch {
            val result = repository.deleteCustomPresetVerified(presetName)
            result.onSuccess {
                selectBuiltInPreset(AppConstants.DEFAULT_PRESET_FLAT)
                emitFeedback(R.string.msg_preset_deleted, listOf(presetName))
            }
        }
    }

    fun saveCustomFrequencyConfig(
        existingId: Long,
        name: String,
        bandCount: Int,
        isDualChannel: Boolean,
        leftCutoffsHz: List<Int>,
        rightCutoffsHz: List<Int>,
        onVerifiedSuccess: () -> Unit
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitFeedback(R.string.err_invalid_name)
            return
        }
        if (!StandardBandConfigs.validateCutoffs(leftCutoffsHz) ||
            (isDualChannel && !StandardBandConfigs.validateCutoffs(rightCutoffsHz))
        ) {
            emitFeedback(R.string.err_invalid_cutoff_order)
            return
        }
        val current = uiState.value.eqState
        viewModelScope.launch {
            val result = repository.saveCustomConfigVerified(
                existingId = existingId,
                name = trimmed,
                bandCount = bandCount,
                isDualChannel = isDualChannel,
                leftCutoffsHz = leftCutoffsHz,
                rightCutoffsHz = rightCutoffsHz,
                currentLeftGains = current.leftGainsDb,
                currentRightGains = current.rightGainsDb
            )
            result.onSuccess { saved ->
                emitFeedback(R.string.msg_config_saved, listOf(saved.name))
                onVerifiedSuccess()
            }.onFailure {
                emitFeedback(R.string.err_invalid_cutoff_order)
            }
        }
    }

    fun deleteCustomFrequencyConfig(configId: Long, onDeleted: () -> Unit) {
        if (configId <= 0L) return
        viewModelScope.launch {
            val result = repository.deleteCustomConfigVerified(configId)
            result.onSuccess {
                emitFeedback(R.string.msg_config_deleted)
                onDeleted()
            }
        }
    }

    fun setSliderSize(size: SliderSize) {
        viewModelScope.launch {
            repository.setSliderSize(size)
        }
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setVisualizerEnabled(enabled)
        }
    }

    fun onVisualizerPermissionDenied() {
        viewModelScope.launch {
            repository.setVisualizerEnabled(false)
            emitFeedback(R.string.err_visualizer_permission_denied)
        }
    }

    fun setVisualizerIntervalMs(intervalMs: Int) {
        viewModelScope.launch {
            repository.setVisualizerIntervalMs(intervalMs)
        }
    }

    fun setPreGainDb(preGainDb: Float) {
        val rounded = ((preGainDb * 10f).roundToInt()) / 10f
        viewModelScope.launch {
            repository.setPreGainDb(rounded)
        }
    }

    fun setBalanceDb(balanceDb: Float) {
        val rounded = ((balanceDb * 10f).roundToInt()) / 10f
        viewModelScope.launch {
            repository.setBalanceDb(rounded)
        }
    }

    fun setOutputVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetIndex = ((clamped / 100f) * maxVolume).roundToInt().coerceIn(0, maxVolume)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        }
        _systemVolumePercent.value = clamped
    }

    fun setEffectsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            repository.setEffectsExpanded(expanded)
        }
    }

    fun setReverbPreset(preset: ReverbPresetType) {
        viewModelScope.launch {
            repository.setReverbPreset(preset)
        }
    }

    fun setBassBoostStrength(strength: Int) {
        viewModelScope.launch {
            repository.setBassBoostStrength(strength)
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        viewModelScope.launch {
            repository.setVirtualizerStrength(strength)
        }
    }

    fun setAutoPanRateHz(rateHz: Float) {
        val rounded = ((rateHz * 10f).roundToInt()) / 10f
        viewModelScope.launch {
            repository.setAutoPanRateHz(rounded)
        }
    }

    fun setMbcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMbcEnabled(enabled)
        }
    }

    fun setMbcPreset(preset: MbcPresetType) {
        viewModelScope.launch {
            repository.setMbcPreset(preset)
        }
    }

    fun updateLimiterSettings(limiter: LimiterSettings) {
        viewModelScope.launch {
            repository.updateLimiter(limiter)
        }
    }

    fun setGlobalMixFallback(enabled: Boolean) {
        viewModelScope.launch {
            repository.setGlobalMixFallback(enabled)
        }
    }

    fun setAppLanguage(languageCode: String) {
        viewModelScope.launch {
            repository.setAppLanguage(languageCode)
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.exportBackupToUriVerified(uri)
            result.onSuccess { byteCount ->
                emitFeedback(R.string.msg_export_verified, listOf(byteCount))
            }.onFailure { err ->
                emitFeedback(R.string.err_export_failed, listOf(err.message ?: "I/O Error"))
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importBackupFromUriVerified(uri)
            result.onSuccess { summary ->
                emitFeedback(
                    R.string.msg_import_verified,
                    listOf(summary.importedPresets, summary.importedConfigs)
                )
            }.onFailure { err ->
                emitFeedback(R.string.err_import_failed, listOf(err.message ?: "Invalid JSON"))
            }
        }
    }

    private fun readSystemVolumePercent(): Int {
        return try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
            ((current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } catch (_: Exception) {
            50
        }
    }

    private fun emitFeedback(resId: Int, args: List<Any> = emptyList()) {
        _feedbackEvents.tryEmit(UiFeedbackEvent.MessageRes(resId, args))
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = DecibellDatabase.getInstance(application)
            val repo = EqPreferencesRepository(application, db.eqDao())
            val engine = DynamicsAudioEngine.getSharedInstance(application)
            return EqualizerViewModel(application, repo, engine) as T
        }
    }
}
