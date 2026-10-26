package com.decibell.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.decibell.data.local.EqConfigEntity
import com.decibell.data.local.EqDao
import com.decibell.data.local.EqPresetEntity
import com.decibell.data.model.EqualizerState
import com.decibell.data.model.LimiterSettings
import com.decibell.data.model.MbcPresetType
import com.decibell.data.model.ReverbPresetType
import com.decibell.data.model.SliderSize
import com.decibell.data.model.StandardBandConfigs
import com.decibell.util.AppConstants
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.decibellDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConstants.DATASTORE_NAME
)

data class ImportSummary(
    val importedPresets: Int,
    val importedConfigs: Int
)

class EqPreferencesRepository(
    private val context: Context,
    private val eqDao: EqDao,
    private val customDataStore: DataStore<Preferences>? = null
) {
    private val dataStore: DataStore<Preferences>
        get() = customDataStore ?: context.decibellDataStore

    private object Keys {
        val EQ_ENABLED = booleanPreferencesKey(AppConstants.KEY_EQ_ENABLED)
        val BAND_COUNT = intPreferencesKey(AppConstants.KEY_BAND_COUNT)
        val CHANNEL_JOINED = booleanPreferencesKey(AppConstants.KEY_CHANNEL_JOINED)
        val ACTIVE_PRESET = stringPreferencesKey(AppConstants.KEY_ACTIVE_PRESET_NAME)
        val ACTIVE_CONFIG_ID = longPreferencesKey(AppConstants.KEY_ACTIVE_CONFIG_ID)
        val LEFT_GAINS = stringPreferencesKey(AppConstants.KEY_LEFT_GAINS)
        val RIGHT_GAINS = stringPreferencesKey(AppConstants.KEY_RIGHT_GAINS)
        val MAX_GAIN_RANGE_DB = floatPreferencesKey(AppConstants.KEY_MAX_GAIN_RANGE_DB)
        val SLIDER_SIZE = stringPreferencesKey(AppConstants.KEY_SLIDER_SIZE)
        val VISUALIZER_ENABLED = booleanPreferencesKey(AppConstants.KEY_VISUALIZER_ENABLED)
        val VISUALIZER_MS = intPreferencesKey(AppConstants.KEY_VISUALIZER_INTERVAL_MS)
        val PRE_GAIN_DB = floatPreferencesKey(AppConstants.KEY_PRE_GAIN_DB)
        val BALANCE_DB = floatPreferencesKey(AppConstants.KEY_BALANCE_DB)
        val EFFECTS_EXPANDED = booleanPreferencesKey(AppConstants.KEY_EFFECTS_EXPANDED)
        val REVERB_PRESET = stringPreferencesKey(AppConstants.KEY_REVERB_PRESET)
        val BASS_BOOST = intPreferencesKey(AppConstants.KEY_BASS_BOOST_STRENGTH)
        val VIRTUALIZER = intPreferencesKey(AppConstants.KEY_VIRTUALIZER_STRENGTH)
        val AUTO_PAN = floatPreferencesKey(AppConstants.KEY_AUTO_PAN_RATE_HZ)
        val MBC_ENABLED = booleanPreferencesKey(AppConstants.KEY_MBC_ENABLED)
        val MBC_PRESET = stringPreferencesKey(AppConstants.KEY_MBC_PRESET)
        val LIMITER_ENABLED = booleanPreferencesKey(AppConstants.KEY_LIMITER_ENABLED)
        val LIMITER_ATTACK = floatPreferencesKey(AppConstants.KEY_LIMITER_ATTACK_MS)
        val LIMITER_RELEASE = floatPreferencesKey(AppConstants.KEY_LIMITER_RELEASE_MS)
        val LIMITER_RATIO = floatPreferencesKey(AppConstants.KEY_LIMITER_RATIO)
        val LIMITER_THRESHOLD = floatPreferencesKey(AppConstants.KEY_LIMITER_THRESHOLD_DB)
        val LIMITER_POST_GAIN = floatPreferencesKey(AppConstants.KEY_LIMITER_POST_GAIN_DB)
        val GLOBAL_MIX_FALLBACK = booleanPreferencesKey(AppConstants.KEY_GLOBAL_MIX_FALLBACK)
        val APP_LANGUAGE = stringPreferencesKey(AppConstants.KEY_APP_LANGUAGE)
    }

    val customPresetsFlow: Flow<List<EqPresetEntity>> = eqDao.observeAllPresets()
    val customConfigsFlow: Flow<List<EqConfigEntity>> = eqDao.observeAllConfigs()

    val stateFlow: Flow<EqualizerState> = dataStore.data.map { prefs ->
        val bandCount = (prefs[Keys.BAND_COUNT] ?: AppConstants.DEFAULT_BAND_COUNT).coerceIn(7, 41)
        val configId = prefs[Keys.ACTIVE_CONFIG_ID] ?: AppConstants.DEFAULT_CONFIG_ID
        val customConfig = if (configId != AppConstants.DEFAULT_CONFIG_ID) {
            eqDao.getConfigById(configId)
        } else {
            null
        }
        val resolvedBandCount = customConfig?.bandCount ?: bandCount
        val defaultCutoffs = StandardBandConfigs.defaultCutoffsForCount(resolvedBandCount)
        val leftCutoffs = customConfig?.let { parseCutoffsCsv(it.leftCutoffsCsv, resolvedBandCount) }
            ?: defaultCutoffs
        val rightCutoffs = customConfig?.let {
            if (it.isDualChannel) parseCutoffsCsv(it.rightCutoffsCsv, resolvedBandCount) else leftCutoffs
        } ?: leftCutoffs

        val maxGainRange = (prefs[Keys.MAX_GAIN_RANGE_DB] ?: AppConstants.DEFAULT_MAX_GAIN_DB)
            .coerceIn(AppConstants.MIN_CUSTOM_MAX_GAIN_DB, AppConstants.MAX_CUSTOM_MAX_GAIN_DB)
        val leftGains = parseGainsCsv(prefs[Keys.LEFT_GAINS], resolvedBandCount)
            .map { it.coerceIn(-maxGainRange, maxGainRange) }
        val rightGains = parseGainsCsv(prefs[Keys.RIGHT_GAINS], resolvedBandCount)
            .map { it.coerceIn(-maxGainRange, maxGainRange) }

        EqualizerState(
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            bandCount = resolvedBandCount,
            isChannelJoined = prefs[Keys.CHANNEL_JOINED] ?: true,
            activePresetName = prefs[Keys.ACTIVE_PRESET] ?: AppConstants.DEFAULT_PRESET_FLAT,
            activeConfigId = customConfig?.id ?: AppConstants.DEFAULT_CONFIG_ID,
            activeConfigName = customConfig?.name ?: "",
            isConfigDualChannel = customConfig?.isDualChannel ?: false,
            leftGainsDb = leftGains,
            rightGainsDb = rightGains,
            leftCutoffsHz = leftCutoffs,
            rightCutoffsHz = rightCutoffs,
            maxGainRangeDb = maxGainRange,
            sliderSize = runCatching {
                SliderSize.valueOf(prefs[Keys.SLIDER_SIZE] ?: SliderSize.SMALL.name)
            }.getOrDefault(SliderSize.SMALL),
            visualizerEnabled = prefs[Keys.VISUALIZER_ENABLED] ?: false,
            visualizerIntervalMs = (prefs[Keys.VISUALIZER_MS] ?: AppConstants.DEFAULT_VISUALIZER_MS)
                .coerceIn(AppConstants.MIN_VISUALIZER_MS, AppConstants.MAX_VISUALIZER_MS),
            preGainDb = (prefs[Keys.PRE_GAIN_DB] ?: 0f)
                .coerceIn(-maxGainRange, maxGainRange),
            balanceDb = (prefs[Keys.BALANCE_DB] ?: 0f)
                .coerceIn(-AppConstants.DEFAULT_BALANCE_RANGE_DB, AppConstants.DEFAULT_BALANCE_RANGE_DB),
            effectsExpanded = prefs[Keys.EFFECTS_EXPANDED] ?: false,
            reverbPreset = runCatching {
                ReverbPresetType.valueOf(prefs[Keys.REVERB_PRESET] ?: ReverbPresetType.NONE.name)
            }.getOrDefault(ReverbPresetType.NONE),
            bassBoostStrength = (prefs[Keys.BASS_BOOST] ?: 0)
                .coerceIn(AppConstants.MIN_EFFECT_STRENGTH, AppConstants.MAX_EFFECT_STRENGTH),
            virtualizerStrength = (prefs[Keys.VIRTUALIZER] ?: 0)
                .coerceIn(AppConstants.MIN_EFFECT_STRENGTH, AppConstants.MAX_EFFECT_STRENGTH),
            autoPanRateHz = (prefs[Keys.AUTO_PAN] ?: 0f)
                .coerceIn(AppConstants.MIN_AUTO_PAN_HZ, AppConstants.MAX_AUTO_PAN_HZ),
            mbcEnabled = prefs[Keys.MBC_ENABLED] ?: false,
            mbcPreset = runCatching {
                MbcPresetType.valueOf(prefs[Keys.MBC_PRESET] ?: MbcPresetType.SPEAKER_BASS_GUARD.name)
            }.getOrDefault(MbcPresetType.SPEAKER_BASS_GUARD),
            limiterSettings = LimiterSettings(
                enabled = prefs[Keys.LIMITER_ENABLED] ?: false,
                attackTimeMs = (prefs[Keys.LIMITER_ATTACK] ?: AppConstants.DEFAULT_LIMITER_ATTACK_MS)
                    .coerceIn(AppConstants.MIN_LIMITER_ATTACK_MS, AppConstants.MAX_LIMITER_ATTACK_MS),
                releaseTimeMs = (prefs[Keys.LIMITER_RELEASE] ?: AppConstants.DEFAULT_LIMITER_RELEASE_MS)
                    .coerceIn(AppConstants.MIN_LIMITER_RELEASE_MS, AppConstants.MAX_LIMITER_RELEASE_MS),
                ratio = (prefs[Keys.LIMITER_RATIO] ?: AppConstants.DEFAULT_LIMITER_RATIO)
                    .coerceIn(AppConstants.MIN_LIMITER_RATIO, AppConstants.MAX_LIMITER_RATIO),
                thresholdDb = (prefs[Keys.LIMITER_THRESHOLD] ?: AppConstants.DEFAULT_LIMITER_THRESHOLD_DB)
                    .coerceIn(AppConstants.MIN_LIMITER_THRESHOLD_DB, AppConstants.MAX_LIMITER_THRESHOLD_DB),
                postGainDb = (prefs[Keys.LIMITER_POST_GAIN] ?: AppConstants.DEFAULT_LIMITER_POST_GAIN_DB)
                    .coerceIn(AppConstants.MIN_LIMITER_POST_GAIN_DB, AppConstants.MAX_LIMITER_POST_GAIN_DB)
            ),
            globalMixFallback = prefs[Keys.GLOBAL_MIX_FALLBACK] ?: true,
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: AppConstants.LANG_SYSTEM
        )
    }

    suspend fun getSnapshot(): EqualizerState = stateFlow.first()

    suspend fun setEqEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setBandConfigStandard(bandCount: Int, currentLeft: List<Float>, currentRight: List<Float>) {
        val resampledLeft = StandardBandConfigs.resampleGains(currentLeft, bandCount)
        val resampledRight = StandardBandConfigs.resampleGains(currentRight, bandCount)
        dataStore.edit {
            it[Keys.ACTIVE_CONFIG_ID] = AppConstants.DEFAULT_CONFIG_ID
            it[Keys.BAND_COUNT] = bandCount
            it[Keys.LEFT_GAINS] = encodeFloatsCsv(resampledLeft)
            it[Keys.RIGHT_GAINS] = encodeFloatsCsv(resampledRight)
        }
    }

    suspend fun setCustomBandConfig(config: EqConfigEntity, currentLeft: List<Float>, currentRight: List<Float>) {
        val resampledLeft = StandardBandConfigs.resampleGains(currentLeft, config.bandCount)
        val resampledRight = StandardBandConfigs.resampleGains(currentRight, config.bandCount)
        dataStore.edit {
            it[Keys.ACTIVE_CONFIG_ID] = config.id
            it[Keys.BAND_COUNT] = config.bandCount
            it[Keys.LEFT_GAINS] = encodeFloatsCsv(resampledLeft)
            it[Keys.RIGHT_GAINS] = encodeFloatsCsv(resampledRight)
        }
    }

    suspend fun setChannelJoined(joined: Boolean, leftGains: List<Float>) {
        dataStore.edit {
            it[Keys.CHANNEL_JOINED] = joined
            if (joined) {
                it[Keys.RIGHT_GAINS] = encodeFloatsCsv(leftGains)
            }
        }
    }

    suspend fun updateGains(
        leftGains: List<Float>,
        rightGains: List<Float>,
        presetName: String = AppConstants.CUSTOM_PRESET_KEY
    ) {
        dataStore.edit {
            it[Keys.LEFT_GAINS] = encodeFloatsCsv(leftGains)
            it[Keys.RIGHT_GAINS] = encodeFloatsCsv(rightGains)
            it[Keys.ACTIVE_PRESET] = presetName
        }
    }

    suspend fun setMaxGainRangeDb(
        maxRangeDb: Float,
        currentLeft: List<Float>,
        currentRight: List<Float>,
        currentPreGain: Float
    ) {
        val clampedRange = maxRangeDb.coerceIn(
            AppConstants.MIN_CUSTOM_MAX_GAIN_DB,
            AppConstants.MAX_CUSTOM_MAX_GAIN_DB
        )
        val clampedLeft = currentLeft.map { it.coerceIn(-clampedRange, clampedRange) }
        val clampedRight = currentRight.map { it.coerceIn(-clampedRange, clampedRange) }
        dataStore.edit {
            it[Keys.MAX_GAIN_RANGE_DB] = clampedRange
            it[Keys.LEFT_GAINS] = encodeFloatsCsv(clampedLeft)
            it[Keys.RIGHT_GAINS] = encodeFloatsCsv(clampedRight)
            it[Keys.PRE_GAIN_DB] = currentPreGain.coerceIn(-clampedRange, clampedRange)
        }
    }

    suspend fun setSliderSize(size: SliderSize) {
        dataStore.edit { it[Keys.SLIDER_SIZE] = size.name }
    }

    suspend fun setVisualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VISUALIZER_ENABLED] = enabled }
    }

    suspend fun setVisualizerIntervalMs(intervalMs: Int) {
        dataStore.edit {
            it[Keys.VISUALIZER_MS] = intervalMs.coerceIn(
                AppConstants.MIN_VISUALIZER_MS,
                AppConstants.MAX_VISUALIZER_MS
            )
        }
    }

    suspend fun setPreGainDb(preGainDb: Float) {
        dataStore.edit {
            it[Keys.PRE_GAIN_DB] = preGainDb.coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
        }
    }

    suspend fun setBalanceDb(balanceDb: Float) {
        dataStore.edit {
            it[Keys.BALANCE_DB] = balanceDb.coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
        }
    }

    suspend fun setEffectsExpanded(expanded: Boolean) {
        dataStore.edit { it[Keys.EFFECTS_EXPANDED] = expanded }
    }

    suspend fun setReverbPreset(preset: ReverbPresetType) {
        dataStore.edit { it[Keys.REVERB_PRESET] = preset.name }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        dataStore.edit {
            it[Keys.BASS_BOOST] = strength.coerceIn(
                AppConstants.MIN_EFFECT_STRENGTH,
                AppConstants.MAX_EFFECT_STRENGTH
            )
        }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        dataStore.edit {
            it[Keys.VIRTUALIZER] = strength.coerceIn(
                AppConstants.MIN_EFFECT_STRENGTH,
                AppConstants.MAX_EFFECT_STRENGTH
            )
        }
    }

    suspend fun setAutoPanRateHz(rateHz: Float) {
        dataStore.edit {
            it[Keys.AUTO_PAN] = rateHz.coerceIn(AppConstants.MIN_AUTO_PAN_HZ, AppConstants.MAX_AUTO_PAN_HZ)
        }
    }

    suspend fun setMbcEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MBC_ENABLED] = enabled }
    }

    suspend fun setMbcPreset(preset: MbcPresetType) {
        dataStore.edit { it[Keys.MBC_PRESET] = preset.name }
    }

    suspend fun updateLimiter(limiter: LimiterSettings) {
        dataStore.edit {
            it[Keys.LIMITER_ENABLED] = limiter.enabled
            it[Keys.LIMITER_ATTACK] = limiter.attackTimeMs.coerceIn(
                AppConstants.MIN_LIMITER_ATTACK_MS,
                AppConstants.MAX_LIMITER_ATTACK_MS
            )
            it[Keys.LIMITER_RELEASE] = limiter.releaseTimeMs.coerceIn(
                AppConstants.MIN_LIMITER_RELEASE_MS,
                AppConstants.MAX_LIMITER_RELEASE_MS
            )
            it[Keys.LIMITER_RATIO] = limiter.ratio.coerceIn(
                AppConstants.MIN_LIMITER_RATIO,
                AppConstants.MAX_LIMITER_RATIO
            )
            it[Keys.LIMITER_THRESHOLD] = limiter.thresholdDb.coerceIn(
                AppConstants.MIN_LIMITER_THRESHOLD_DB,
                AppConstants.MAX_LIMITER_THRESHOLD_DB
            )
            it[Keys.LIMITER_POST_GAIN] = limiter.postGainDb.coerceIn(
                AppConstants.MIN_LIMITER_POST_GAIN_DB,
                AppConstants.MAX_LIMITER_POST_GAIN_DB
            )
        }
    }

    suspend fun setGlobalMixFallback(enabled: Boolean) {
        dataStore.edit { it[Keys.GLOBAL_MIX_FALLBACK] = enabled }
    }

    suspend fun setAppLanguage(languageCode: String) {
        dataStore.edit { it[Keys.APP_LANGUAGE] = languageCode }
    }

    suspend fun saveCustomPresetVerified(
        name: String,
        state: EqualizerState
    ): Result<EqPresetEntity> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty preset name"))
        }
        val existing = eqDao.getPresetByName(trimmed)
        val entity = EqPresetEntity(
            id = existing?.id ?: 0L,
            name = trimmed,
            bandCount = state.bandCount,
            isChannelJoined = state.isChannelJoined,
            leftGainsCsv = encodeFloatsCsv(state.leftGainsDb),
            rightGainsCsv = encodeFloatsCsv(state.rightGainsDb),
            preGainDb = state.preGainDb,
            balanceDb = state.balanceDb,
            updatedAtMillis = System.currentTimeMillis()
        )
        val rowId = eqDao.insertPreset(entity)
        val verified = eqDao.getPresetById(rowId)
        if (verified != null && verified.name == trimmed && verified.leftGainsCsv.isNotEmpty()) {
            dataStore.edit { it[Keys.ACTIVE_PRESET] = trimmed }
            Result.success(verified)
        } else {
            Result.failure(IllegalStateException("Database verification failed for preset $trimmed"))
        }
    }

    suspend fun deleteCustomPresetVerified(presetName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val deletedRows = eqDao.deletePresetByName(presetName)
        val checkAfter = eqDao.getPresetByName(presetName)
        if (deletedRows > 0 && checkAfter == null) {
            dataStore.edit { it[Keys.ACTIVE_PRESET] = AppConstants.DEFAULT_PRESET_FLAT }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Preset deletion could not be verified"))
        }
    }

    suspend fun saveCustomConfigVerified(
        existingId: Long,
        name: String,
        bandCount: Int,
        isDualChannel: Boolean,
        leftCutoffsHz: List<Int>,
        rightCutoffsHz: List<Int>,
        currentLeftGains: List<Float>,
        currentRightGains: List<Float>
    ): Result<EqConfigEntity> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty configuration name"))
        }
        if (leftCutoffsHz.size != bandCount || !StandardBandConfigs.validateCutoffs(leftCutoffsHz)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid left cut-off frequencies"))
        }
        val resolvedRight = if (isDualChannel) rightCutoffsHz else leftCutoffsHz
        if (resolvedRight.size != bandCount || !StandardBandConfigs.validateCutoffs(resolvedRight)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid right cut-off frequencies"))
        }

        val entity = EqConfigEntity(
            id = if (existingId > 0) existingId else 0L,
            name = trimmed,
            bandCount = bandCount,
            isDualChannel = isDualChannel,
            leftCutoffsCsv = encodeIntsCsv(leftCutoffsHz),
            rightCutoffsCsv = encodeIntsCsv(resolvedRight),
            updatedAtMillis = System.currentTimeMillis()
        )
        val rowId = eqDao.insertConfig(entity)
        val verified = eqDao.getConfigById(rowId)
        if (verified != null && verified.bandCount == bandCount) {
            setCustomBandConfig(verified, currentLeftGains, currentRightGains)
            Result.success(verified)
        } else {
            Result.failure(IllegalStateException("Database verification failed for frequency config"))
        }
    }

    suspend fun deleteCustomConfigVerified(configId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val deleted = eqDao.deleteConfigById(configId)
        val check = eqDao.getConfigById(configId)
        if (deleted > 0 && check == null) {
            dataStore.edit {
                if (it[Keys.ACTIVE_CONFIG_ID] == configId) {
                    it[Keys.ACTIVE_CONFIG_ID] = AppConstants.DEFAULT_CONFIG_ID
                }
            }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Config deletion could not be verified"))
        }
    }

    suspend fun exportBackupToUriVerified(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val presets = eqDao.getAllPresetsOnce()
            val configs = eqDao.getAllConfigsOnce()
            val root = JSONObject().apply {
                put("app", "Decibell")
                put("version", 1)
                put("exportedAtMillis", System.currentTimeMillis())

                val presetsArray = JSONArray()
                for (p in presets) {
                    presetsArray.put(
                        JSONObject().apply {
                            put("name", p.name)
                            put("bandCount", p.bandCount)
                            put("isChannelJoined", p.isChannelJoined)
                            put("leftGainsCsv", p.leftGainsCsv)
                            put("rightGainsCsv", p.rightGainsCsv)
                            put("preGainDb", p.preGainDb.toDouble())
                            put("balanceDb", p.balanceDb.toDouble())
                        }
                    )
                }
                put("presets", presetsArray)

                val configsArray = JSONArray()
                for (c in configs) {
                    configsArray.put(
                        JSONObject().apply {
                            put("name", c.name)
                            put("bandCount", c.bandCount)
                            put("isDualChannel", c.isDualChannel)
                            put("leftCutoffsCsv", c.leftCutoffsCsv)
                            put("rightCutoffsCsv", c.rightCutoffsCsv)
                        }
                    )
                }
                put("configs", configsArray)
            }

            val payloadBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(payloadBytes)
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open output stream"))

            // Verify written file by reading it back and parsing JSON structure
            val readBackText = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot verify output stream"))

            val readBackBytes = readBackText.toByteArray(Charsets.UTF_8).size
            val verifiedJson = JSONObject(readBackText)
            if (readBackBytes > 0 && verifiedJson.optString("app") == "Decibell") {
                Result.success(readBackBytes)
            } else {
                Result.failure(IllegalStateException("Exported file failed verification check"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackupFromUriVerified(uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            val rawText = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream"))

            if (rawText.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Backup file is empty"))
            }

            val root = JSONObject(rawText)
            var presetsImported = 0
            var configsImported = 0

            val presetsArray = root.optJSONArray("presets")
            if (presetsArray != null) {
                for (i in 0 until presetsArray.length()) {
                    val obj = presetsArray.getJSONObject(i)
                    val name = obj.optString("name").trim()
                    val bandCount = obj.optInt("bandCount", 7).coerceIn(7, 41)
                    val leftCsv = obj.optString("leftGainsCsv")
                    val rightCsv = obj.optString("rightGainsCsv")
                    if (name.isNotEmpty() && leftCsv.isNotEmpty()) {
                        val existing = eqDao.getPresetByName(name)
                        val rowId = eqDao.insertPreset(
                            EqPresetEntity(
                                id = existing?.id ?: 0L,
                                name = name,
                                bandCount = bandCount,
                                isChannelJoined = obj.optBoolean("isChannelJoined", true),
                                leftGainsCsv = leftCsv,
                                rightGainsCsv = rightCsv.ifEmpty { leftCsv },
                                preGainDb = obj.optDouble("preGainDb", 0.0).toFloat(),
                                balanceDb = obj.optDouble("balanceDb", 0.0).toFloat()
                            )
                        )
                        if (eqDao.getPresetById(rowId) != null) {
                            presetsImported++
                        }
                    }
                }
            }

            val configsArray = root.optJSONArray("configs")
            if (configsArray != null) {
                for (i in 0 until configsArray.length()) {
                    val obj = configsArray.getJSONObject(i)
                    val name = obj.optString("name").trim()
                    val bandCount = obj.optInt("bandCount", 7).coerceIn(7, 41)
                    val leftCsv = obj.optString("leftCutoffsCsv")
                    val rightCsv = obj.optString("rightCutoffsCsv")
                    val parsedLeft = parseCutoffsCsv(leftCsv, bandCount)
                    if (name.isNotEmpty() && StandardBandConfigs.validateCutoffs(parsedLeft)) {
                        val rowId = eqDao.insertConfig(
                            EqConfigEntity(
                                name = name,
                                bandCount = bandCount,
                                isDualChannel = obj.optBoolean("isDualChannel", false),
                                leftCutoffsCsv = encodeIntsCsv(parsedLeft),
                                rightCutoffsCsv = if (rightCsv.isNotEmpty()) rightCsv else encodeIntsCsv(parsedLeft)
                            )
                        )
                        if (eqDao.getConfigById(rowId) != null) {
                            configsImported++
                        }
                    }
                }
            }

            if (presetsImported == 0 && configsImported == 0 &&
                (presetsArray == null || presetsArray.length() == 0) &&
                (configsArray == null || configsArray.length() == 0)
            ) {
                Result.failure(IllegalArgumentException("No valid presets or configs found in JSON"))
            } else {
                Result.success(ImportSummary(presetsImported, configsImported))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun encodeFloatsCsv(values: List<Float>): String =
            values.joinToString(",") { value ->
                val clamped = value.coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB)
                ((Math.round(clamped * 10f)) / 10f).toString()
            }

        fun encodeIntsCsv(values: List<Int>): String =
            values.joinToString(",") { it.toString() }

        fun parseGainsCsv(csv: String?, targetCount: Int): List<Float> {
            if (csv.isNullOrBlank()) {
                return List(targetCount) { AppConstants.DEFAULT_GAIN_DB }
            }
            val parsed = csv.split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
                .map { it.coerceIn(AppConstants.MIN_GAIN_DB, AppConstants.MAX_GAIN_DB) }
            if (parsed.isEmpty()) {
                return List(targetCount) { AppConstants.DEFAULT_GAIN_DB }
            }
            return StandardBandConfigs.resampleGains(parsed, targetCount)
        }

        fun parseCutoffsCsv(csv: String?, targetCount: Int): List<Int> {
            val defaultList = StandardBandConfigs.defaultCutoffsForCount(targetCount)
            if (csv.isNullOrBlank()) return defaultList
            val parsed = csv.split(",").mapNotNull { it.trim().toIntOrNull() }
            return if (parsed.size == targetCount && StandardBandConfigs.validateCutoffs(parsed)) {
                parsed
            } else {
                defaultList
            }
        }
    }
}
