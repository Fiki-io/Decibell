package com.decibell.data.model

import android.media.audiofx.PresetReverb
import com.decibell.util.AppConstants
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class SliderSize {
    SMALL,
    MEDIUM,
    LARGE
}

enum class ReverbPresetType(val effectValue: Short) {
    NONE(PresetReverb.PRESET_NONE),
    SMALL_ROOM(PresetReverb.PRESET_SMALLROOM),
    MEDIUM_ROOM(PresetReverb.PRESET_MEDIUMROOM),
    LARGE_ROOM(PresetReverb.PRESET_LARGEROOM),
    MEDIUM_HALL(PresetReverb.PRESET_MEDIUMHALL),
    LARGE_HALL(PresetReverb.PRESET_LARGEHALL),
    PLATE(PresetReverb.PRESET_PLATE)
}

enum class MbcPresetType {
    SPEAKER_BASS_GUARD,
    VOCAL_PRESENCE,
    WARM_BROADCAST,
    GENTLE_MASTER,
    PUNCHY_DYNAMICS
}

data class ActiveAudioSession(
    val sessionId: Int,
    val packageName: String,
    val openedAtMillis: Long
)

data class LimiterSettings(
    val enabled: Boolean = false,
    val attackTimeMs: Float = AppConstants.DEFAULT_LIMITER_ATTACK_MS,
    val releaseTimeMs: Float = AppConstants.DEFAULT_LIMITER_RELEASE_MS,
    val ratio: Float = AppConstants.DEFAULT_LIMITER_RATIO,
    val thresholdDb: Float = AppConstants.DEFAULT_LIMITER_THRESHOLD_DB,
    val postGainDb: Float = AppConstants.DEFAULT_LIMITER_POST_GAIN_DB
)

data class EqualizerState(
    val eqEnabled: Boolean = false,
    val bandCount: Int = AppConstants.DEFAULT_BAND_COUNT,
    val isChannelJoined: Boolean = true,
    val activePresetName: String = AppConstants.DEFAULT_PRESET_FLAT,
    val activeConfigId: Long = AppConstants.DEFAULT_CONFIG_ID,
    val activeConfigName: String = "",
    val isConfigDualChannel: Boolean = false,
    val leftGainsDb: List<Float> = List(AppConstants.DEFAULT_BAND_COUNT) { AppConstants.DEFAULT_GAIN_DB },
    val rightGainsDb: List<Float> = List(AppConstants.DEFAULT_BAND_COUNT) { AppConstants.DEFAULT_GAIN_DB },
    val leftCutoffsHz: List<Int> = StandardBandConfigs.defaultCutoffsForCount(AppConstants.DEFAULT_BAND_COUNT),
    val rightCutoffsHz: List<Int> = StandardBandConfigs.defaultCutoffsForCount(AppConstants.DEFAULT_BAND_COUNT),
    val maxGainRangeDb: Float = AppConstants.DEFAULT_MAX_GAIN_DB,
    val sliderSize: SliderSize = SliderSize.SMALL,
    val visualizerEnabled: Boolean = false,
    val visualizerIntervalMs: Int = AppConstants.DEFAULT_VISUALIZER_MS,
    val preGainDb: Float = AppConstants.DEFAULT_GAIN_DB,
    val balanceDb: Float = 0.0f,
    val outputVolumePercent: Int = 50,
    val effectsExpanded: Boolean = false,
    val reverbPreset: ReverbPresetType = ReverbPresetType.NONE,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val autoPanRateHz: Float = 0.0f,
    val mbcEnabled: Boolean = false,
    val mbcPreset: MbcPresetType = MbcPresetType.SPEAKER_BASS_GUARD,
    val limiterSettings: LimiterSettings = LimiterSettings(),
    val globalMixFallback: Boolean = true,
    val appLanguage: String = AppConstants.LANG_SYSTEM
)

object StandardBandConfigs {
    private val CUTOFFS_7 = listOf(100, 250, 1000, 2900, 6100, 9000, 16000)

    private val CUTOFFS_11 = listOf(
        45, 90, 180, 355, 710, 1400, 2800, 5600, 11200, 15000, 18500
    )

    private val CUTOFFS_15 = listOf(
        32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 12500, 18000
    )

    private val CUTOFFS_21 = listOf(
        28, 42, 63, 95, 140, 210, 315, 475, 710, 1060,
        1600, 2400, 3600, 5300, 7500, 10000, 12500, 14500, 16500, 18200, 19800
    )

    private val CUTOFFS_27 = listOf(
        25, 34, 46, 63, 85, 115, 155, 210, 280, 380,
        510, 690, 930, 1250, 1700, 2300, 3100, 4200, 5600,
        7500, 9500, 11500, 13500, 15200, 16800, 18400, 19800
    )

    private val CUTOFFS_31 = listOf(
        22, 28, 35, 44, 56, 70, 88, 112, 140, 178,
        224, 280, 355, 447, 560, 710, 890, 1120, 1400, 1780,
        2240, 2800, 3550, 4470, 5600, 7100, 8900, 11200, 14000, 17200, 20000
    )

    private val CUTOFFS_37 = generateLogarithmicCutoffs(37, 22, 20000)
    private val CUTOFFS_41 = generateLogarithmicCutoffs(41, 20, 20000)

    fun defaultCutoffsForCount(bandCount: Int): List<Int> {
        return when (bandCount) {
            7 -> CUTOFFS_7
            11 -> CUTOFFS_11
            15 -> CUTOFFS_15
            21 -> CUTOFFS_21
            27 -> CUTOFFS_27
            31 -> CUTOFFS_31
            37 -> CUTOFFS_37
            41 -> CUTOFFS_41
            else -> generateLogarithmicCutoffs(bandCount.coerceIn(7, 41), 22, 20000)
        }
    }

    fun computeBandCenterHz(lowerHz: Int, upperHz: Int): Int {
        if (upperHz <= lowerHz) return 0
        val mid = (lowerHz + upperHz) / 2
        return when {
            mid >= 1000 -> ((mid + 50) / 100) * 100
            mid >= 100 -> ((mid + 5) / 10) * 10
            else -> mid.coerceAtLeast(20)
        }
    }

    fun displayFrequenciesForCutoffs(cutoffsHz: List<Int>): List<Int> {
        return cutoffsHz.mapIndexed { index, upperHz ->
            val lowerHz = if (index == 0) 0 else cutoffsHz[index - 1]
            computeBandCenterHz(lowerHz, upperHz)
        }
    }

    fun validateCutoffs(cutoffsHz: List<Int>): Boolean {
        if (cutoffsHz.isEmpty()) return false
        var previous = 0
        for (hz in cutoffsHz) {
            if (hz < AppConstants.MIN_CUTOFF_HZ || hz > AppConstants.MAX_CUTOFF_HZ) return false
            if (hz <= previous) return false
            previous = hz
        }
        return true
    }

    fun resampleGains(oldGains: List<Float>, targetCount: Int): List<Float> {
        if (oldGains.size == targetCount) return oldGains
        if (oldGains.isEmpty()) return List(targetCount) { 0f }
        if (targetCount <= 1) return listOf(oldGains.first())
        return List(targetCount) { index ->
            val ratio = index.toFloat() / (targetCount - 1).toFloat()
            val srcPos = ratio * (oldGains.size - 1)
            val lowIdx = srcPos.toInt().coerceIn(0, oldGains.lastIndex)
            val highIdx = (lowIdx + 1).coerceIn(0, oldGains.lastIndex)
            val fraction = srcPos - lowIdx
            val interpolated = oldGains[lowIdx] + (oldGains[highIdx] - oldGains[lowIdx]) * fraction
            (interpolated * 10f).roundToInt() / 10f
        }
    }

    private fun generateLogarithmicCutoffs(count: Int, minHz: Int, maxHz: Int): List<Int> {
        val logMin = ln(minHz.toDouble())
        val logMax = ln(maxHz.toDouble())
        val result = ArrayList<Int>(count)
        var prev = minHz - 1
        for (i in 0 until count) {
            val fraction = i.toDouble() / (count - 1).coerceAtLeast(1)
            val raw = Math.E.pow(logMin + fraction * (logMax - logMin)).roundToInt()
            val nextVal = maxOf(prev + 1, raw).coerceAtMost(maxHz - (count - 1 - i))
            result.add(nextVal)
            prev = nextVal
        }
        return result
    }
}

object BuiltInPresets {
    // 7-band reference curves at [50, 260, 500, 1700, 4200, 7400, 12000] Hz
    private val PRESET_MAP: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass" to listOf(7.5f, 5.0f, 1.5f, 0f, 0f, 0.5f, 1.0f),
        "Acoustic" to listOf(4.0f, 3.0f, 1.0f, 1.5f, 3.0f, 3.5f, 2.5f),
        "Classical" to listOf(4.5f, 3.0f, -1.5f, -1.5f, 0f, 3.0f, 4.5f),
        "Dance" to listOf(6.5f, 4.5f, 1.5f, 0f, 2.0f, 4.0f, 3.5f),
        "Electronic" to listOf(6.0f, 4.0f, 0f, -2.0f, 2.0f, 4.5f, 5.5f),
        "Hip-Hop" to listOf(7.0f, 4.5f, 0f, -1.5f, 1.5f, 2.5f, 3.5f),
        "Jazz" to listOf(3.5f, 2.5f, 1.0f, 2.0f, -1.5f, 2.0f, 3.5f),
        "Pop" to listOf(-1.5f, 2.5f, 4.5f, 4.0f, 1.5f, -1.0f, -1.5f),
        "Rock" to listOf(5.5f, 3.5f, -2.5f, -1.0f, 2.5f, 4.5f, 5.5f),
        "Treble" to listOf(0f, 0f, 0f, 1.5f, 4.0f, 6.5f, 8.0f),
        "Vocal" to listOf(-3.0f, -1.5f, 3.5f, 5.5f, 4.5f, 1.5f, -1.0f)
    )

    val ALL_NAMES: List<String> = PRESET_MAP.keys.toList()

    fun gainsForPreset(presetName: String, bandCount: Int): List<Float> {
        val baseGains = PRESET_MAP[presetName] ?: List(7) { 0f }
        return StandardBandConfigs.resampleGains(baseGains, bandCount)
    }
}
