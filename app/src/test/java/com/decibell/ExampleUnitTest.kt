package com.decibell

import com.decibell.data.model.BuiltInPresets
import com.decibell.data.model.StandardBandConfigs
import com.decibell.domain.engine.FftBinBandMapper
import com.decibell.util.AppConstants
import com.decibell.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun standardBandCutoffs_areValidAndStrictlyAscending() {
        for (count in AppConstants.ALL_CONFIG_BAND_COUNTS) {
            val cutoffs = StandardBandConfigs.defaultCutoffsForCount(count)
            assertEquals(count, cutoffs.size)
            assertTrue(
                "Cutoffs for $count bands must be strictly ascending within 20..20000 Hz",
                StandardBandConfigs.validateCutoffs(cutoffs)
            )
        }
    }

    @Test
    fun cutoffValidation_rejectsDescendingOrOutOfRangeValues() {
        assertFalse(StandardBandConfigs.validateCutoffs(listOf(100, 250, 200, 1000)))
        assertFalse(StandardBandConfigs.validateCutoffs(listOf(10, 100, 1000)))
        assertFalse(StandardBandConfigs.validateCutoffs(listOf(100, 1000, 25000)))
        assertTrue(StandardBandConfigs.validateCutoffs(listOf(100, 250, 1000, 2900, 6100, 9000, 16000)))
    }

    @Test
    fun resampleGains_preservesEndpointsAcrossBandCounts() {
        val bass7 = BuiltInPresets.gainsForPreset("Bass", 7)
        val bass31 = StandardBandConfigs.resampleGains(bass7, 31)
        assertEquals(31, bass31.size)
        assertEquals(bass7.first(), bass31.first(), 0.01f)
        assertEquals(bass7.last(), bass31.last(), 0.01f)
    }

    @Test
    fun frequencyFormatting_matchesExpectedLabels() {
        assertEquals("50", LocaleHelper.formatFrequencyLabel(50))
        assertEquals("1.7k", LocaleHelper.formatFrequencyLabel(1700))
        assertEquals("12k", LocaleHelper.formatFrequencyLabel(12000))
        assertEquals(
            listOf(50, 180, 630, 2000, 4500, 7600, 12500),
            StandardBandConfigs.displayFrequenciesForCutoffs(
                listOf(100, 250, 1000, 2900, 6100, 9000, 16000)
            )
        )
    }

    @Test
    fun fftBinBandMapper_guaranteesActiveBinsAndSingleSliderResponseAcrossAllConfigs() {
        for (count in AppConstants.ALL_CONFIG_BAND_COUNTS) {
            val cutoffs = StandardBandConfigs.defaultCutoffsForCount(count)
            val flatGains = List(count) { 0f }
            val mappedFlat = FftBinBandMapper.mapToHardwareBands(cutoffs, flatGains)
            assertEquals(count, mappedFlat.size)

            var prevStopBin = 0
            var prevCutoffHz = 0f
            for ((idx, spec) in mappedFlat.withIndex()) {
                assertTrue(
                    "Band $idx in $count-band config must own >= 1 FFT bin",
                    spec.stopBinIndex >= spec.startBinIndex && spec.stopBinIndex > prevStopBin
                )
                assertTrue(
                    "Hardware cutoff for band $idx in $count-band must be strictly increasing",
                    spec.cutoffFrequencyHz > prevCutoffHz
                )
                assertEquals(0f, spec.gainDb, 0.001f)
                prevStopBin = spec.stopBinIndex
                prevCutoffHz = spec.cutoffFrequencyHz
            }

            // Verify moving every single slider individually produces non-zero hardware gain
            for (sliderIdx in 0 until count) {
                val singleSliderGains = MutableList(count) { 0f }.apply { this[sliderIdx] = 12f }
                val mappedActive = FftBinBandMapper.mapToHardwareBands(cutoffs, singleSliderGains)
                assertEquals(
                    "Primary hardware band $sliderIdx in $count-band must reflect +12 dB",
                    12f,
                    mappedActive[sliderIdx].gainDb,
                    0.01f
                )
            }

            // Verify boosting all sliders simultaneously to maxGain does not overshoot maxGain
            val allBoosted = List(count) { 15f }
            val mappedAllBoosted = FftBinBandMapper.mapToHardwareBands(cutoffs, allBoosted, maxGainRangeDb = 15f)
            for (spec in mappedAllBoosted) {
                assertEquals(15f, spec.gainDb, 0.01f)
            }
        }
    }

    @Test
    fun fftBinBandMapper_handlesTightCustomConfigWithoutZeroBins() {
        val tightCustomCutoffs = listOf(20, 22, 24, 26, 28, 30, 1000)
        val gains = listOf(10f, 0f, 0f, 0f, 0f, 0f, 0f)
        val mapped = FftBinBandMapper.mapToHardwareBands(tightCustomCutoffs, gains)
        assertEquals(7, mapped.size)
        for (i in mapped.indices) {
            val ownedBins = mapped[i].stopBinIndex - mapped[i].startBinIndex + 1
            assertTrue("Tight custom band $i must own at least 1 FFT bin", ownedBins >= 1)
        }
        assertEquals(10f, mapped[0].gainDb, 0.01f)
        assertTrue("Adjacent sub-bass band should receive coupled gain", mapped[1].gainDb > 0f)
    }

    @Test
    fun expandToFixedHardwareBands_produces41BandsForAllConfigsWithoutZeroBins() {
        for (count in AppConstants.ALL_CONFIG_BAND_COUNTS) {
            val cutoffs = StandardBandConfigs.defaultCutoffsForCount(count)
            val gains = BuiltInPresets.gainsForPreset("Rock", count)
            val logical = FftBinBandMapper.mapToHardwareBands(cutoffs, gains)
            val expanded = FftBinBandMapper.expandToFixedHardwareBands(
                logicalBands = logical,
                targetHardwareCount = AppConstants.MAX_HARDWARE_EQ_BANDS
            )
            assertEquals(AppConstants.MAX_HARDWARE_EQ_BANDS, expanded.size)
            var prevStop = 0
            for ((idx, spec) in expanded.withIndex()) {
                assertTrue(
                    "Expanded band $idx from $count-band config must own >= 1 FFT bin",
                    spec.stopBinIndex >= spec.startBinIndex && spec.stopBinIndex > prevStop
                )
                prevStop = spec.stopBinIndex
            }
            assertEquals(logical.first().gainDb, expanded.first().gainDb, 0.001f)
            assertEquals(logical.last().gainDb, expanded.last().gainDb, 0.001f)
        }
    }
}
