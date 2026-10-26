package com.decibell.domain.engine

import com.decibell.util.AppConstants
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class HardwareEqBandSpec(
    val cutoffFrequencyHz: Float,
    val gainDb: Float,
    val startBinIndex: Int,
    val stopBinIndex: Int
)

object FftBinBandMapper {

    /**
     * Maps user cut-off frequencies and slider gains onto the discrete FFT frequency bin grid
     * used by AOSP DynamicsProcessing (DPFrequency.cpp).
     *
     * Guarantees:
     * 1. Every hardware band owns at least 1 distinct FFT bin (stopBinIndex >= startBinIndex),
     *    preventing AOSP DPFrequency from assigning 0 bins to closely-spaced low-frequency sliders.
     * 2. Hardware cut-offs are aligned to exact FFT bin centers (stopBinIndex * binWidthHz)
     *    so native bin rounding is deterministic.
     * 3. Narrow low-frequency bands (< 160 Hz or shifted by bin collisions) couple proportionally
     *    into adjacent FFT bins with a monotonic headroom blend so every single slider produces
     *    an audible, measurable change across the entire gain range without overshoot.
     */
    fun mapToHardwareBands(
        userCutoffsHz: List<Int>,
        userGainsDb: List<Float>,
        maxGainRangeDb: Float = AppConstants.DEFAULT_MAX_GAIN_DB,
        sampleRateHz: Int = AppConstants.DP_DEFAULT_OUTPUT_SAMPLE_RATE_HZ,
        fftBlockSize: Int = AppConstants.DP_MAX_FFT_BLOCK_SIZE
    ): List<HardwareEqBandSpec> {
        val bandCount = userCutoffsHz.size
        if (bandCount == 0) return emptyList()

        val safeSampleRate = sampleRateHz.coerceIn(8000, 192000)
        val safeFftSize = fftBlockSize.coerceIn(64, AppConstants.DP_MAX_FFT_BLOCK_SIZE)
        val binWidthHz = safeSampleRate.toFloat() / safeFftSize.toFloat()
        val maxUsableBin = max(bandCount + 1, safeFftSize / 2 - 1)
        val safeMaxGain = maxGainRangeDb.coerceIn(
            AppConstants.MIN_CUSTOM_MAX_GAIN_DB,
            AppConstants.MAX_CUSTOM_MAX_GAIN_DB
        )

        val clampedCutoffs = IntArray(bandCount) { i ->
            userCutoffsHz[i].coerceIn(AppConstants.MIN_CUTOFF_HZ, AppConstants.MAX_CUTOFF_HZ)
        }
        val clampedGains = FloatArray(bandCount) { i ->
            userGainsDb.getOrElse(i) { 0f }.coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
        }

        // Step 1: Compute natural preferred FFT bin index for each user cutoff.
        // If two adjacent bands round to the same bin index, allow the lower band to take floor(exactBin)
        // when available so it does not unnecessarily push subsequent bands upward.
        val preferredBins = IntArray(bandCount)
        for (i in 0 until bandCount) {
            val exactBin = clampedCutoffs[i] / binWidthHz
            val rounded = exactBin.roundToInt().coerceAtLeast(1)
            if (i + 1 < bandCount) {
                val nextExact = clampedCutoffs[i + 1] / binWidthHz
                val nextRounded = nextExact.roundToInt().coerceAtLeast(1)
                val floored = floor(exactBin).toInt()
                preferredBins[i] = if (rounded >= nextRounded && floored >= 1) {
                    floored
                } else {
                    rounded
                }
            } else {
                preferredBins[i] = rounded
            }
        }

        // Step 2: Forward & backward pass to guarantee strictly increasing stopBinIndex (>= 1 bin per band).
        val stopBins = IntArray(bandCount)
        var prevStop = 0
        for (i in 0 until bandCount) {
            val nextStop = max(prevStop + 1, preferredBins[i])
            stopBins[i] = nextStop
            prevStop = nextStop
        }

        stopBins[bandCount - 1] = min(stopBins[bandCount - 1], maxUsableBin)
        for (i in bandCount - 2 downTo 0) {
            stopBins[i] = min(stopBins[i], stopBins[i + 1] - 1).coerceAtLeast(i + 1)
        }

        // Step 3: Compute effective gain per hardware band with narrow-band / sub-bass bin coupling.
        val effectiveGains = FloatArray(bandCount)
        for (i in 0 until bandCount) {
            val selfGain = clampedGains[i]
            val lowerHz = if (i == 0) 0 else clampedCutoffs[i - 1]
            val upperHz = clampedCutoffs[i]
            val bandWidthHz = (upperHz - lowerHz).coerceAtLeast(1)
            val startBin = if (i == 0) 1 else stopBins[i - 1] + 1
            val ownedBins = (stopBins[i] - startBin + 1).coerceAtLeast(1)

            // Couple adjacent bands when operating in the sub-bass/low-bass region (< 160 Hz),
            // when a band owns <= 2 FFT bins, or when bin indices were shifted to prevent collision.
            var neighborWeightedSum = 0f
            var neighborWeightTotal = 0f

            if (i - 1 >= 0) {
                val prevUpper = clampedCutoffs[i - 1]
                val prevLower = if (i - 1 == 0) 0 else clampedCutoffs[i - 2]
                val prevWidth = (prevUpper - prevLower).coerceAtLeast(1)
                val prevShifted = stopBins[i - 1] != preferredBins[i - 1]
                if (prevUpper <= AppConstants.SUB_BASS_COUPLING_MAX_HZ ||
                    prevWidth < binWidthHz * 2.5f ||
                    ownedBins <= 2 ||
                    prevShifted
                ) {
                    val w = computeCouplingWeight(prevWidth, binWidthHz, prevShifted)
                    neighborWeightedSum += clampedGains[i - 1] * w
                    neighborWeightTotal += w
                }
            }

            if (i + 1 < bandCount) {
                val nextUpper = clampedCutoffs[i + 1]
                val nextLower = clampedCutoffs[i]
                val nextWidth = (nextUpper - nextLower).coerceAtLeast(1)
                val nextShifted = stopBins[i + 1] != preferredBins[i + 1]
                if (upperHz <= AppConstants.SUB_BASS_COUPLING_MAX_HZ ||
                    bandWidthHz < binWidthHz * 2.5f ||
                    ownedBins <= 2 ||
                    nextShifted
                ) {
                    val w = computeCouplingWeight(nextWidth, binWidthHz, nextShifted)
                    neighborWeightedSum += clampedGains[i + 1] * w
                    neighborWeightTotal += w
                }
            }

            val coupledNeighborGain = if (neighborWeightTotal > 0f) {
                val maxCoupling = neighborWeightTotal.coerceAtMost(AppConstants.NARROW_BAND_COUPLING_FACTOR)
                (neighborWeightedSum / neighborWeightTotal) * maxCoupling
            } else {
                0f
            }

            // Headroom-preserving blend:
            // - If selfGain == 0, hardware band receives coupledNeighborGain so narrow neighbor sliders
            //   activate this FFT bin proportionally.
            // - As |selfGain| approaches safeMaxGain, headroomFactor approaches 0 so own slider reaches
            //   exact target dB without overshoot when adjacent sliders are boosted together.
            val headroomFactor = (1.0f - (abs(selfGain) / safeMaxGain)).coerceIn(0.0f, 1.0f)
            effectiveGains[i] = (selfGain + coupledNeighborGain * headroomFactor)
                .coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
        }

        return List(bandCount) { i ->
            val startBin = if (i == 0) 1 else stopBins[i - 1] + 1
            val stopBin = stopBins[i]
            val hwCutoffHz = (stopBin * binWidthHz).coerceIn(
                binWidthHz,
                AppConstants.MAX_CUTOFF_HZ.toFloat()
            )
            HardwareEqBandSpec(
                cutoffFrequencyHz = hwCutoffHz,
                gainDb = effectiveGains[i],
                startBinIndex = startBin,
                stopBinIndex = stopBin
            )
        }
    }

    /**
     * Expands logical FFT-mapped bands (e.g., 7, 11, 15, 21, 27, 31, or 37 bands) into a fixed
     * hardware band count (41 bands) by subdividing wide bands into contiguous FFT sub-bands
     * that share the exact same dB gain as their parent band.
     *
     * This allows DynamicsProcessing to stay initialized at a constant 41-band stage count,
     * eliminating native AudioEffect teardown/re-creation crackle when switching configurations.
     */
    fun expandToFixedHardwareBands(
        logicalBands: List<HardwareEqBandSpec>,
        targetHardwareCount: Int = AppConstants.MAX_HARDWARE_EQ_BANDS,
        sampleRateHz: Int = AppConstants.DP_DEFAULT_OUTPUT_SAMPLE_RATE_HZ,
        fftBlockSize: Int = AppConstants.DP_MAX_FFT_BLOCK_SIZE
    ): List<HardwareEqBandSpec> {
        if (logicalBands.isEmpty()) return emptyList()
        if (logicalBands.size == targetHardwareCount) return logicalBands
        if (logicalBands.size > targetHardwareCount) return logicalBands.take(targetHardwareCount)

        val safeSampleRate = sampleRateHz.coerceIn(8000, 192000)
        val safeFftSize = fftBlockSize.coerceIn(64, AppConstants.DP_MAX_FFT_BLOCK_SIZE)
        val binWidthHz = safeSampleRate.toFloat() / safeFftSize.toFloat()
        val maxCutoff = AppConstants.MAX_CUTOFF_HZ.toFloat()

        val expanded = logicalBands.toMutableList()
        while (expanded.size < targetHardwareCount) {
            var widestIdx = -1
            var maxSpan = 1
            for (i in expanded.indices) {
                val span = expanded[i].stopBinIndex - expanded[i].startBinIndex + 1
                if (span > maxSpan) {
                    maxSpan = span
                    widestIdx = i
                }
            }

            if (widestIdx < 0) {
                val last = expanded.last()
                val nextBin = last.stopBinIndex + 1
                expanded.add(
                    HardwareEqBandSpec(
                        cutoffFrequencyHz = (nextBin * binWidthHz).coerceIn(binWidthHz, maxCutoff),
                        gainDb = last.gainDb,
                        startBinIndex = nextBin,
                        stopBinIndex = nextBin
                    )
                )
            } else {
                val target = expanded[widestIdx]
                val midBin = (target.startBinIndex + target.stopBinIndex) / 2
                val leftHalf = HardwareEqBandSpec(
                    cutoffFrequencyHz = (midBin * binWidthHz).coerceIn(binWidthHz, maxCutoff),
                    gainDb = target.gainDb,
                    startBinIndex = target.startBinIndex,
                    stopBinIndex = midBin
                )
                val rightHalf = HardwareEqBandSpec(
                    cutoffFrequencyHz = target.cutoffFrequencyHz,
                    gainDb = target.gainDb,
                    startBinIndex = midBin + 1,
                    stopBinIndex = target.stopBinIndex
                )
                expanded[widestIdx] = leftHalf
                expanded.add(widestIdx + 1, rightHalf)
            }
        }
        return expanded
    }

    private fun computeCouplingWeight(
        bandWidthHz: Int,
        binWidthHz: Float,
        wasShifted: Boolean
    ): Float {
        val ratio = (bandWidthHz.toFloat() / (binWidthHz * 3.0f)).coerceIn(0.0f, 1.0f)
        val base = AppConstants.NARROW_BAND_COUPLING_FACTOR * (1.0f - ratio * 0.55f)
        return if (wasShifted) {
            max(base, AppConstants.NARROW_BAND_COUPLING_FACTOR)
        } else {
            base
        }
    }
}
