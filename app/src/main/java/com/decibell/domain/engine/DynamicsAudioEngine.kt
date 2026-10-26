package com.decibell.domain.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.decibell.data.model.ActiveAudioSession
import com.decibell.data.model.EqualizerState
import com.decibell.data.model.LimiterSettings
import com.decibell.data.model.MbcPresetType
import com.decibell.data.model.ReverbPresetType
import com.decibell.util.AppConstants
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DynamicsAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private class SessionEffectBundle(
        val sessionId: Int,
        val hardwareBandCount: Int,
        val dynamicsProcessing: DynamicsProcessing
    ) {
        var bassBoost: BassBoost? = null
        var virtualizer: Virtualizer? = null
        var presetReverb: PresetReverb? = null

        var lastLeftPreBands: List<Pair<Float, Float>>? = null
        var lastRightPreBands: List<Pair<Float, Float>>? = null
        var lastLeftPostBands: List<Pair<Float, Float>>? = null
        var lastRightPostBands: List<Pair<Float, Float>>? = null
        var lastLeftInputGainDb: Float? = null
        var lastRightInputGainDb: Float? = null
        var lastMbcEnabled: Boolean? = null
        var lastMbcPreset: MbcPresetType? = null
        var lastLimiterSettings: LimiterSettings? = null
        var lastBassBoostStrength: Int = 0
        var lastVirtualizerStrength: Int = 0
        var lastReverbPreset: ReverbPresetType = ReverbPresetType.NONE
        var isHardwareEnabled: Boolean = false

        fun releaseAll() {
            runCatching {
                dynamicsProcessing.enabled = false
                dynamicsProcessing.release()
            }
            runCatching {
                bassBoost?.enabled = false
                bassBoost?.release()
            }
            runCatching {
                virtualizer?.enabled = false
                virtualizer?.release()
            }
            runCatching {
                presetReverb?.enabled = false
                presetReverb?.release()
            }
            bassBoost = null
            virtualizer = null
            presetReverb = null
            isHardwareEnabled = false
        }
    }

    private val sessionBundles = mutableMapOf<Int, SessionEffectBundle>()
    private var visualizer: Visualizer? = null
    private var visualizerSessionId: Int = -1
    private var visualizerIntervalMs: Int = AppConstants.DEFAULT_VISUALIZER_MS
    private var smoothedBins = FloatArray(AppConstants.VISUALIZER_BINS) { 0f }

    private var autoPanJob: Job? = null
    private var autoPanPhase: Double = 0.0
    @Volatile
    private var latestState: EqualizerState = EqualizerState()

    @Synchronized
    fun applyState(
        state: EqualizerState,
        detectedSessions: List<ActiveAudioSession>
    ): EngineStatus {
        latestState = state

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            releaseAll()
            val status = EngineStatus.Error("API < 28")
            AudioSessionRegistry.updateEngineStatus(status)
            return status
        }

        if (!state.eqEnabled) {
            stopVisualizer()
            stopAutoPan()
            sessionBundles.values.forEach { bundle ->
                applySoftBypassApi28(bundle)
            }
            val status = EngineStatus.Inactive
            AudioSessionRegistry.updateEngineStatus(status)
            return status
        }

        val targetSessions = resolveTargetSessions(state, detectedSessions)
        // Release sessions no longer active
        val obsoleteIds = sessionBundles.keys.filterNot { it in targetSessions }
        for (id in obsoleteIds) {
            sessionBundles.remove(id)?.releaseAll()
        }

        var lastError: String? = null
        var verifiedActiveSessionId: Int? = null

        for (sessionId in targetSessions) {
            val result = applyToSessionApi28(sessionId, state)
            if (result.isSuccess) {
                if (verifiedActiveSessionId == null) {
                    verifiedActiveSessionId = sessionId
                }
            } else {
                lastError = result.exceptionOrNull()?.message ?: "Effect initialization failed"
            }
        }

        // If target session failed and global fallback is enabled, try session 0
        if (verifiedActiveSessionId == null && state.globalMixFallback && !targetSessions.contains(0)) {
            val fallbackResult = applyToSessionApi28(AppConstants.GLOBAL_AUDIO_SESSION_ID, state)
            if (fallbackResult.isSuccess) {
                verifiedActiveSessionId = AppConstants.GLOBAL_AUDIO_SESSION_ID
            } else {
                lastError = fallbackResult.exceptionOrNull()?.message ?: lastError
            }
        }

        updateAutoPanLoop(state)
        updateVisualizerState(state, verifiedActiveSessionId ?: AppConstants.GLOBAL_AUDIO_SESSION_ID)

        val finalStatus = when {
            verifiedActiveSessionId != null && verifiedActiveSessionId > 0 -> {
                val pkg = detectedSessions.firstOrNull { it.sessionId == verifiedActiveSessionId }
                    ?.packageName ?: "audio.session"
                EngineStatus.ActiveSession(verifiedActiveSessionId, pkg, state.bandCount)
            }
            verifiedActiveSessionId == AppConstants.GLOBAL_AUDIO_SESSION_ID -> {
                EngineStatus.ActiveGlobalMix(state.bandCount)
            }
            else -> {
                EngineStatus.Error(lastError ?: "Unable to attach DynamicsProcessing")
            }
        }

        AudioSessionRegistry.updateEngineStatus(finalStatus)
        return finalStatus
    }

    private fun resolveTargetSessions(
        state: EqualizerState,
        detectedSessions: List<ActiveAudioSession>
    ): List<Int> {
        val ids = detectedSessions.map { it.sessionId }.distinct()
        return if (ids.isNotEmpty()) {
            ids
        } else {
            listOf(AppConstants.GLOBAL_AUDIO_SESSION_ID)
        }
    }

    /**
     * Keeps DynamicsProcessing enabled in unity pass-through (0.0 dB flat Pre/Post EQ, 0.0 dB input gain,
     * MBC and Limiter bypassed) so AudioFlinger never detaches/re-attaches the STFT overlap-add buffer
     * while music is playing.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun applySoftBypassApi28(bundle: SessionEffectBundle) {
        val dp = bundle.dynamicsProcessing
        val hwCount = bundle.hardwareBandCount
        runCatching {
            val templateLeft = bundle.lastLeftPreBands
            val templateRight = bundle.lastRightPreBands
            if (templateLeft != null && templateRight != null) {
                val unityLeft = templateLeft.map { it.first to 0.0f }
                val unityRight = templateRight.map { it.first to 0.0f }
                if (bundle.lastLeftPreBands != unityLeft || bundle.lastRightPreBands != unityRight) {
                    val eqLeft = buildEqStageApi28(unityLeft)
                    if (unityLeft == unityRight) {
                        dp.setPreEqAllChannelsTo(eqLeft)
                    } else {
                        dp.setPreEqByChannelIndex(0, eqLeft)
                        dp.setPreEqByChannelIndex(1, buildEqStageApi28(unityRight))
                    }
                    bundle.lastLeftPreBands = unityLeft
                    bundle.lastRightPreBands = unityRight
                }
                if (bundle.lastLeftPostBands != unityLeft || bundle.lastRightPostBands != unityRight) {
                    val eqLeft = buildEqStageApi28(unityLeft)
                    if (unityLeft == unityRight) {
                        dp.setPostEqAllChannelsTo(eqLeft)
                    } else {
                        dp.setPostEqByChannelIndex(0, eqLeft)
                        dp.setPostEqByChannelIndex(1, buildEqStageApi28(unityRight))
                    }
                    bundle.lastLeftPostBands = unityLeft
                    bundle.lastRightPostBands = unityRight
                }
            } else {
                val fallbackEq = DynamicsProcessing.Eq(true, true, hwCount)
                val stepHz = AppConstants.MAX_CUTOFF_HZ.toFloat() / hwCount.toFloat()
                for (i in 0 until hwCount) {
                    fallbackEq.setBand(i, DynamicsProcessing.EqBand(true, (i + 1) * stepHz, 0.0f))
                }
                dp.setPreEqAllChannelsTo(fallbackEq)
                dp.setPostEqAllChannelsTo(fallbackEq)
            }

            applyChannelInputGainsApi28(bundle, 0.0f, 0.0f, 0.0f)

            if (bundle.lastMbcEnabled != false) {
                val preset = bundle.lastMbcPreset ?: MbcPresetType.SPEAKER_BASS_GUARD
                dp.setMbcAllChannelsTo(buildMbcApi28(false, preset))
                bundle.lastMbcEnabled = false
            }

            val currentLimiter = bundle.lastLimiterSettings
            if (currentLimiter == null || currentLimiter.enabled) {
                val disabledLimiter = (currentLimiter ?: LimiterSettings()).copy(enabled = false)
                dp.setLimiterAllChannelsTo(buildLimiterApi28(disabledLimiter))
                bundle.lastLimiterSettings = disabledLimiter
            }

            bundle.bassBoost?.let { bb ->
                if (bundle.lastBassBoostStrength > 0) {
                    runCatching {
                        if (bb.strengthSupported) bb.setStrength(0)
                        bb.enabled = false
                    }
                    bundle.lastBassBoostStrength = 0
                }
            }
            bundle.virtualizer?.let { virt ->
                if (bundle.lastVirtualizerStrength > 0) {
                    runCatching {
                        if (virt.strengthSupported) virt.setStrength(0)
                        virt.enabled = false
                    }
                    bundle.lastVirtualizerStrength = 0
                }
            }
            bundle.presetReverb?.let { rev ->
                if (bundle.lastReverbPreset != ReverbPresetType.NONE) {
                    runCatching {
                        rev.enabled = false
                    }
                    bundle.lastReverbPreset = ReverbPresetType.NONE
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyToSessionApi28(
        sessionId: Int,
        state: EqualizerState
    ): Result<Unit> = runCatching {
        val hwBandCount = AppConstants.MAX_HARDWARE_EQ_BANDS
        val existing = sessionBundles[sessionId]
        val bundle = if (existing == null || existing.hardwareBandCount != hwBandCount) {
            existing?.releaseAll()
            createBundleApi28(sessionId, hwBandCount).also {
                sessionBundles[sessionId] = it
            }
        } else {
            existing
        }

        val dp = bundle.dynamicsProcessing
        val userBandCount = state.bandCount.coerceIn(1, hwBandCount)

        // 1. Map user bands onto the 4096-FFT bin grid and expand to fixed 41 hardware bands
        val sampleRateHz = resolveOutputSampleRateHz()
        val effectiveLeftCutoffs = List(userBandCount) { idx ->
            state.leftCutoffsHz.getOrElse(idx) { 1000 }
        }
        val effectiveRightCutoffs = if (state.isConfigDualChannel) {
            List(userBandCount) { idx ->
                state.rightCutoffsHz.getOrElse(idx) { effectiveLeftCutoffs[idx] }
            }
        } else {
            effectiveLeftCutoffs
        }
        val effectiveLeftGains = List(userBandCount) { idx ->
            state.leftGainsDb.getOrElse(idx) { 0f }
                .coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
        }
        val effectiveRightGains = if (state.isChannelJoined) {
            effectiveLeftGains
        } else {
            List(userBandCount) { idx ->
                state.rightGainsDb.getOrElse(idx) { effectiveLeftGains[idx] }
                    .coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
            }
        }

        val logicalLeftBands = FftBinBandMapper.mapToHardwareBands(
            userCutoffsHz = effectiveLeftCutoffs,
            userGainsDb = effectiveLeftGains,
            maxGainRangeDb = state.maxGainRangeDb,
            sampleRateHz = sampleRateHz,
            fftBlockSize = AppConstants.DP_MAX_FFT_BLOCK_SIZE
        )
        val leftHardwareBands = FftBinBandMapper.expandToFixedHardwareBands(
            logicalBands = logicalLeftBands,
            targetHardwareCount = hwBandCount,
            sampleRateHz = sampleRateHz,
            fftBlockSize = AppConstants.DP_MAX_FFT_BLOCK_SIZE
        )

        val rightHardwareBands = if (state.isChannelJoined && !state.isConfigDualChannel) {
            leftHardwareBands
        } else {
            val logicalRightBands = FftBinBandMapper.mapToHardwareBands(
                userCutoffsHz = effectiveRightCutoffs,
                userGainsDb = effectiveRightGains,
                maxGainRangeDb = state.maxGainRangeDb,
                sampleRateHz = sampleRateHz,
                fftBlockSize = AppConstants.DP_MAX_FFT_BLOCK_SIZE
            )
            FftBinBandMapper.expandToFixedHardwareBands(
                logicalBands = logicalRightBands,
                targetHardwareCount = hwBandCount,
                sampleRateHz = sampleRateHz,
                fftBlockSize = AppConstants.DP_MAX_FFT_BLOCK_SIZE
            )
        }

        val nextLeftPre = ArrayList<Pair<Float, Float>>(hwBandCount)
        val nextRightPre = ArrayList<Pair<Float, Float>>(hwBandCount)
        val nextLeftPost = ArrayList<Pair<Float, Float>>(hwBandCount)
        val nextRightPost = ArrayList<Pair<Float, Float>>(hwBandCount)

        for (i in 0 until hwBandCount) {
            val leftSpec = leftHardwareBands[i]
            val rightSpec = rightHardwareBands[i]

            val (leftPreGainBand, leftPostGainBand) = splitStageGainDb(leftSpec.gainDb)
            val (rightPreGainBand, rightPostGainBand) = splitStageGainDb(rightSpec.gainDb)

            nextLeftPre.add(leftSpec.cutoffFrequencyHz to leftPreGainBand)
            nextRightPre.add(rightSpec.cutoffFrequencyHz to rightPreGainBand)
            nextLeftPost.add(leftSpec.cutoffFrequencyHz to leftPostGainBand)
            nextRightPost.add(rightSpec.cutoffFrequencyHz to rightPostGainBand)
        }

        if (bundle.lastLeftPreBands != nextLeftPre || bundle.lastRightPreBands != nextRightPre) {
            val leftPreEq = buildEqStageApi28(nextLeftPre)
            if (nextLeftPre == nextRightPre) {
                dp.setPreEqAllChannelsTo(leftPreEq)
            } else {
                dp.setPreEqByChannelIndex(0, leftPreEq)
                dp.setPreEqByChannelIndex(1, buildEqStageApi28(nextRightPre))
            }
            bundle.lastLeftPreBands = nextLeftPre
            bundle.lastRightPreBands = nextRightPre
        }

        if (bundle.lastLeftPostBands != nextLeftPost || bundle.lastRightPostBands != nextRightPost) {
            val leftPostEq = buildEqStageApi28(nextLeftPost)
            if (nextLeftPost == nextRightPost) {
                dp.setPostEqAllChannelsTo(leftPostEq)
            } else {
                dp.setPostEqByChannelIndex(0, leftPostEq)
                dp.setPostEqByChannelIndex(1, buildEqStageApi28(nextRightPost))
            }
            bundle.lastLeftPostBands = nextLeftPost
            bundle.lastRightPostBands = nextRightPost
        }

        // 2. Configure Pre-gain & L/R Balance (only when changed)
        applyChannelInputGainsApi28(bundle, state.preGainDb, state.balanceDb, 0f)

        // 3. Configure Multi-band Compressor (MBC) (only when changed)
        if (bundle.lastMbcEnabled != state.mbcEnabled || bundle.lastMbcPreset != state.mbcPreset) {
            val mbc = buildMbcApi28(state.mbcEnabled, state.mbcPreset)
            dp.setMbcAllChannelsTo(mbc)
            bundle.lastMbcEnabled = state.mbcEnabled
            bundle.lastMbcPreset = state.mbcPreset
        }

        // 4. Configure Limiter (only when changed)
        if (bundle.lastLimiterSettings != state.limiterSettings) {
            val limiter = buildLimiterApi28(state.limiterSettings)
            dp.setLimiterAllChannelsTo(limiter)
            bundle.lastLimiterSettings = state.limiterSettings
        }

        // Enable once and verify
        if (!bundle.isHardwareEnabled || !dp.enabled) {
            dp.enabled = true
            if (!dp.enabled) {
                throw IllegalStateException("Hardware rejected enabling DynamicsProcessing on session $sessionId")
            }
            bundle.isHardwareEnabled = true
        }

        // 5. Configure Auxiliary Audio Effects lazily (only when active or changed)
        val targetBassBoost = state.bassBoostStrength.coerceIn(0, 1000)
        if (targetBassBoost != bundle.lastBassBoostStrength) {
            if (targetBassBoost > 0 && bundle.bassBoost == null) {
                bundle.bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
            }
            bundle.bassBoost?.let { bb ->
                runCatching {
                    val active = targetBassBoost > 0
                    if (active && bb.strengthSupported) {
                        bb.setStrength(targetBassBoost.toShort())
                    }
                    if (bb.enabled != active) {
                        bb.enabled = active
                    }
                }
            }
            bundle.lastBassBoostStrength = targetBassBoost
        }

        val targetVirtualizer = state.virtualizerStrength.coerceIn(0, 1000)
        if (targetVirtualizer != bundle.lastVirtualizerStrength) {
            if (targetVirtualizer > 0 && bundle.virtualizer == null) {
                bundle.virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
            }
            bundle.virtualizer?.let { virt ->
                runCatching {
                    val active = targetVirtualizer > 0
                    if (active && virt.strengthSupported) {
                        virt.setStrength(targetVirtualizer.toShort())
                    }
                    if (virt.enabled != active) {
                        virt.enabled = active
                    }
                }
            }
            bundle.lastVirtualizerStrength = targetVirtualizer
        }

        val targetReverb = state.reverbPreset
        if (targetReverb != bundle.lastReverbPreset) {
            if (targetReverb != ReverbPresetType.NONE && bundle.presetReverb == null) {
                bundle.presetReverb = runCatching { PresetReverb(0, sessionId) }.getOrNull()
            }
            bundle.presetReverb?.let { rev ->
                runCatching {
                    val active = targetReverb != ReverbPresetType.NONE
                    if (active) {
                        rev.preset = targetReverb.effectValue
                    }
                    if (rev.enabled != active) {
                        rev.enabled = active
                    }
                }
            }
            bundle.lastReverbPreset = targetReverb
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildEqStageApi28(bands: List<Pair<Float, Float>>): DynamicsProcessing.Eq {
        val eq = DynamicsProcessing.Eq(true, true, bands.size)
        for (i in bands.indices) {
            val (cutoffHz, gainDb) = bands[i]
            eq.setBand(i, DynamicsProcessing.EqBand(true, cutoffHz, gainDb))
        }
        return eq
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createBundleApi28(sessionId: Int, hardwareBandCount: Int): SessionEffectBundle {
        val dp = runCatching {
            val highResConfig = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                hardwareBandCount,
                true,
                MBC_BAND_COUNT,
                true,
                hardwareBandCount,
                true
            ).setPreferredFrameDuration(AppConstants.DP_PREFERRED_FRAME_DURATION_MS).build()
            DynamicsProcessing(0, sessionId, highResConfig)
        }.getOrElse {
            val fallbackConfig = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                hardwareBandCount,
                true,
                MBC_BAND_COUNT,
                true,
                hardwareBandCount,
                true
            ).setPreferredFrameDuration(AppConstants.DP_FALLBACK_FRAME_DURATION_MS).build()
            DynamicsProcessing(0, sessionId, fallbackConfig)
        }

        return SessionEffectBundle(
            sessionId = sessionId,
            hardwareBandCount = hardwareBandCount,
            dynamicsProcessing = dp
        )
    }

    private fun resolveOutputSampleRateHz(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rawRate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        return if (rawRate != null && rawRate in 8000..192000) {
            rawRate
        } else {
            AppConstants.DP_DEFAULT_OUTPUT_SAMPLE_RATE_HZ
        }
    }

    private fun splitStageGainDb(totalGainDb: Float): Pair<Float, Float> {
        val threshold = AppConstants.DEFAULT_MAX_GAIN_DB
        return if (kotlin.math.abs(totalGainDb) <= threshold) {
            totalGainDb to 0.0f
        } else {
            val half = totalGainDb * 0.5f
            half to (totalGainDb - half)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyChannelInputGainsApi28(
        bundle: SessionEffectBundle,
        preGainDb: Float,
        balanceDb: Float,
        panOffsetDb: Float
    ) {
        val leftAttenuation = if (balanceDb > 0f) -balanceDb else 0f
        val rightAttenuation = if (balanceDb < 0f) balanceDb else 0f

        val leftTotal = (preGainDb + leftAttenuation - panOffsetDb)
            .coerceIn(AppConstants.MIN_GAIN_DB * 2f, AppConstants.MAX_GAIN_DB)
        val rightTotal = (preGainDb + rightAttenuation + panOffsetDb)
            .coerceIn(AppConstants.MIN_GAIN_DB * 2f, AppConstants.MAX_GAIN_DB)

        if (bundle.lastLeftInputGainDb != leftTotal) {
            bundle.dynamicsProcessing.setInputGainbyChannel(0, leftTotal)
            bundle.lastLeftInputGainDb = leftTotal
        }
        if (bundle.lastRightInputGainDb != rightTotal) {
            bundle.dynamicsProcessing.setInputGainbyChannel(1, rightTotal)
            bundle.lastRightInputGainDb = rightTotal
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildMbcApi28(
        enabled: Boolean,
        preset: MbcPresetType
    ): DynamicsProcessing.Mbc {
        val mbc = DynamicsProcessing.Mbc(true, enabled, MBC_BAND_COUNT)
        val cutoffs = floatArrayOf(120f, 350f, 1000f, 3200f, 8000f, 20000f)

        for (i in 0 until MBC_BAND_COUNT) {
            val band = when (preset) {
                MbcPresetType.SPEAKER_BASS_GUARD -> {
                    // Tight compression on sub-bass & low-bass to prevent speaker distortion
                    val isBass = i <= 1
                    DynamicsProcessing.MbcBand(
                        enabled,
                        cutoffs[i],
                        if (isBass) 5.0f else 15.0f,
                        if (isBass) 90.0f else 120.0f,
                        if (isBass) 4.5f else 1.5f,
                        if (isBass) -18.0f else -10.0f,
                        6.0f,
                        -70.0f,
                        1.0f,
                        0.0f,
                        if (isBass) -1.5f else 0.5f
                    )
                }
                MbcPresetType.VOCAL_PRESENCE -> {
                    val isMid = i in 2..3
                    DynamicsProcessing.MbcBand(
                        enabled,
                        cutoffs[i],
                        10.0f,
                        100.0f,
                        if (isMid) 2.8f else 2.0f,
                        if (isMid) -16.0f else -14.0f,
                        5.0f,
                        -70.0f,
                        1.0f,
                        0.0f,
                        if (isMid) 2.5f else 0.0f
                    )
                }
                MbcPresetType.WARM_BROADCAST -> {
                    DynamicsProcessing.MbcBand(
                        enabled,
                        cutoffs[i],
                        8.0f,
                        110.0f,
                        3.2f,
                        -16.0f,
                        6.0f,
                        -65.0f,
                        1.0f,
                        0.0f,
                        if (i <= 2) 1.5f else 0.5f
                    )
                }
                MbcPresetType.GENTLE_MASTER -> {
                    DynamicsProcessing.MbcBand(
                        enabled,
                        cutoffs[i],
                        18.0f,
                        160.0f,
                        1.8f,
                        -12.0f,
                        8.0f,
                        -75.0f,
                        1.0f,
                        0.0f,
                        1.0f
                    )
                }
                MbcPresetType.PUNCHY_DYNAMICS -> {
                    DynamicsProcessing.MbcBand(
                        enabled,
                        cutoffs[i],
                        22.0f,
                        85.0f,
                        3.5f,
                        -15.0f,
                        4.0f,
                        -70.0f,
                        1.0f,
                        0.0f,
                        if (i == 0 || i == 4) 2.0f else 0.5f
                    )
                }
            }
            mbc.setBand(i, band)
        }
        return mbc
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildLimiterApi28(settings: LimiterSettings): DynamicsProcessing.Limiter {
        return DynamicsProcessing.Limiter(
            true,
            settings.enabled,
            0,
            settings.attackTimeMs.coerceIn(
                AppConstants.MIN_LIMITER_ATTACK_MS,
                AppConstants.MAX_LIMITER_ATTACK_MS
            ),
            settings.releaseTimeMs.coerceIn(
                AppConstants.MIN_LIMITER_RELEASE_MS,
                AppConstants.MAX_LIMITER_RELEASE_MS
            ),
            settings.ratio.coerceIn(
                AppConstants.MIN_LIMITER_RATIO,
                AppConstants.MAX_LIMITER_RATIO
            ),
            settings.thresholdDb.coerceIn(
                AppConstants.MIN_LIMITER_THRESHOLD_DB,
                AppConstants.MAX_LIMITER_THRESHOLD_DB
            ),
            settings.postGainDb.coerceIn(
                AppConstants.MIN_LIMITER_POST_GAIN_DB,
                AppConstants.MAX_LIMITER_POST_GAIN_DB
            )
        )
    }

    private fun updateAutoPanLoop(state: EqualizerState) {
        if (!state.eqEnabled || state.autoPanRateHz <= 0.02f || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            stopAutoPan()
            return
        }
        if (autoPanJob?.isActive == true) return

        autoPanJob = scope.launch {
            while (isActive) {
                val current = latestState
                if (!current.eqEnabled || current.autoPanRateHz <= 0.02f) break
                val stepRadians = 2.0 * PI * current.autoPanRateHz * (AppConstants.AUTO_PAN_TICK_MS / 1000.0)
                autoPanPhase = (autoPanPhase + stepRadians) % (2.0 * PI)
                val panOffsetDb = (sin(autoPanPhase) * AppConstants.AUTO_PAN_DEPTH_DB).toFloat()

                synchronized(this@DynamicsAudioEngine) {
                    sessionBundles.values.forEach { bundle ->
                        runCatching {
                            applyChannelInputGainsApi28(
                                bundle,
                                current.preGainDb,
                                current.balanceDb,
                                panOffsetDb
                            )
                        }
                    }
                }
                delay(AppConstants.AUTO_PAN_TICK_MS)
            }
        }
    }

    private fun stopAutoPan() {
        autoPanJob?.cancel()
        autoPanJob = null
        autoPanPhase = 0.0
    }

    private fun updateVisualizerState(state: EqualizerState, activeSessionId: Int) {
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!state.eqEnabled || !state.visualizerEnabled || !hasRecordPermission) {
            stopVisualizer()
            return
        }

        visualizerIntervalMs = state.visualizerIntervalMs
        if (visualizer != null && visualizerSessionId == activeSessionId) {
            return
        }

        stopVisualizer()
        runCatching {
            val vis = Visualizer(activeSessionId)
            vis.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            val captureSize = range[1].coerceAtMost(512).coerceAtLeast(range[0])
            vis.captureSize = captureSize

            val maxRate = Visualizer.getMaxCaptureRate()
            val targetRateMilliHz = (1000_000 / state.visualizerIntervalMs.coerceIn(100, 1000))
                .coerceIn(1000, maxRate)

            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        processFftBytes(fft)
                    }
                },
                targetRateMilliHz,
                false,
                true
            )
            vis.enabled = true
            visualizer = vis
            visualizerSessionId = activeSessionId
        }.onFailure {
            stopVisualizer()
        }
    }

    private fun processFftBytes(fft: ByteArray) {
        val numBins = AppConstants.VISUALIZER_BINS
        val availablePairs = (fft.size / 2) - 1
        if (availablePairs <= 0) return

        val smoothingFactor = (visualizerIntervalMs.coerceIn(100, 1000) / 1200f).coerceIn(0.15f, 0.80f)
        val nextBins = FloatArray(numBins)

        for (binIdx in 0 until numBins) {
            val startPair = (binIdx * availablePairs) / numBins + 1
            val endPair = (((binIdx + 1) * availablePairs) / numBins + 1).coerceAtLeast(startPair + 1)
            var sumMag = 0f
            var count = 0
            for (pair in startPair until endPair.coerceAtMost(fft.size / 2)) {
                val r = fft[pair * 2].toFloat()
                val i = fft[pair * 2 + 1].toFloat()
                val mag = hypot(r, i)
                sumMag += mag
                count++
            }
            val avgMag = if (count > 0) sumMag / count else 0f
            val dbNormalized = if (avgMag > 1f) {
                (20f * log10(avgMag) / 42f).coerceIn(0f, 1f)
            } else {
                0f
            }
            val prev = smoothedBins[binIdx]
            val smoothed = prev * smoothingFactor + dbNormalized * (1f - smoothingFactor)
            smoothedBins[binIdx] = smoothed
            nextBins[binIdx] = smoothed
        }
        AudioSessionRegistry.updateSpectrum(nextBins)
    }

    private fun stopVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        visualizerSessionId = -1
        smoothedBins.fill(0f)
        AudioSessionRegistry.clearSpectrum()
    }

    @Synchronized
    fun releaseAll() {
        stopVisualizer()
        stopAutoPan()
        sessionBundles.values.forEach { it.releaseAll() }
        sessionBundles.clear()
        AudioSessionRegistry.updateEngineStatus(EngineStatus.Inactive)
    }

    companion object {
        private const val MBC_BAND_COUNT = 6

        @Volatile
        private var sharedInstance: DynamicsAudioEngine? = null
        private val engineScope = CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        )

        fun getSharedInstance(context: Context): DynamicsAudioEngine {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: DynamicsAudioEngine(
                    context.applicationContext,
                    engineScope
                ).also { sharedInstance = it }
            }
        }
    }
}
