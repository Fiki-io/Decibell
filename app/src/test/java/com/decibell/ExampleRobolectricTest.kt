package com.decibell

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.decibell.data.local.DecibellDatabase
import com.decibell.data.model.EqualizerState
import com.decibell.data.repository.EqPreferencesRepository
import com.decibell.domain.engine.AudioSessionRegistry
import com.decibell.receiver.AudioSessionReceiver
import com.decibell.util.AppConstants
import com.decibell.util.LocaleHelper
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: DecibellDatabase
    private lateinit var repository: EqPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DecibellDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EqPreferencesRepository(context, db.eqDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun appNameAndMultiLanguageStrings_resolveCorrectly() {
        assertEquals("Decibell", context.getString(R.string.app_name))

        val idContext = LocaleHelper.wrapContext(context, AppConstants.LANG_ID)
        assertEquals("SIMPAN", idContext.getString(R.string.action_save))

        val esContext = LocaleHelper.wrapContext(context, AppConstants.LANG_ES)
        assertEquals("GUARDAR", esContext.getString(R.string.action_save))

        val jaContext = LocaleHelper.wrapContext(context, AppConstants.LANG_JA)
        assertEquals("保存", jaContext.getString(R.string.action_save))
    }

    @Test
    fun audioSessionReceiver_registersAndRemovesExternalMediaSession() {
        val receiver = AudioSessionReceiver()
        val openIntent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 409)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "com.spotify.music")
        }
        receiver.onReceive(context, openIntent)

        val activeAfterOpen = AudioSessionRegistry.activeSessions.value
        assertTrue(activeAfterOpen.any { it.sessionId == 409 && it.packageName == "com.spotify.music" })

        val closeIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 409)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "com.spotify.music")
        }
        receiver.onReceive(context, closeIntent)

        val activeAfterClose = AudioSessionRegistry.activeSessions.value
        assertTrue(activeAfterClose.none { it.sessionId == 409 })
    }

    @Test
    fun presetAndCustomConfigPersistence_andJsonBackupVerification() {
        runBlocking {
            val state = EqualizerState(
                bandCount = 7,
                maxGainRangeDb = 40f,
                isChannelJoined = false,
                leftGainsDb = listOf(28.5f, 15.0f, 0f, -12.5f, 8.0f, 22.0f, 35.5f),
                rightGainsDb = listOf(26.0f, 14.5f, 0f, -10.0f, 9.5f, 20.5f, 34.0f),
                preGainDb = -2.0f,
                balanceDb = 1.0f
            )

            repository.setMaxGainRangeDb(
                maxRangeDb = 40f,
                currentLeft = state.leftGainsDb,
                currentRight = state.rightGainsDb,
                currentPreGain = state.preGainDb
            )
            val savedState = repository.stateFlow.first()
            assertEquals(40f, savedState.maxGainRangeDb, 0.01f)
            assertEquals(35.5f, savedState.leftGainsDb.last(), 0.01f)

            val presetResult = repository.saveCustomPresetVerified("Studio Reference", state)
            assertTrue(presetResult.isSuccess)

            val configResult = repository.saveCustomConfigVerified(
                existingId = 0L,
                name = "7-Band Dual Reference",
                bandCount = 7,
                isDualChannel = true,
                leftCutoffsHz = listOf(90, 240, 950, 2800, 6000, 9200, 16500),
                rightCutoffsHz = listOf(100, 260, 1000, 3000, 6200, 9400, 16800),
                currentLeftGains = state.leftGainsDb,
                currentRightGains = state.rightGainsDb
            )
            assertTrue(configResult.isSuccess)

            val tempBackupFile = File(context.cacheDir, "decibell_test_backup.json")
            val uri = Uri.fromFile(tempBackupFile)

            val exportResult = repository.exportBackupToUriVerified(uri)
            assertTrue(exportResult.isSuccess)
            assertTrue((exportResult.getOrNull() ?: 0) > 50)

            // Delete and re-import to verify round-trip
            repository.deleteCustomPresetVerified("Studio Reference")
            val importResult = repository.importBackupFromUriVerified(uri)
            assertTrue(importResult.isSuccess)
            assertEquals(1, importResult.getOrNull()?.importedPresets)

            tempBackupFile.delete()
        }
    }

    @Test
    fun localeSwitching_updatesBothWrappedAndBaseContextResources() {
        val jaContext = LocaleHelper.wrapContext(
            context,
            AppConstants.LANG_JA
        )
        assertEquals("設定", jaContext.getString(R.string.title_settings))
        assertEquals("保存", jaContext.getString(R.string.action_save))
        assertEquals("設定", context.getString(R.string.title_settings))

        val idContext = LocaleHelper.wrapContext(
            context,
            AppConstants.LANG_ID
        )
        assertEquals("Pengaturan", idContext.getString(R.string.title_settings))
        assertEquals("SIMPAN", idContext.getString(R.string.action_save))
        assertEquals("Konfigurasi EQ (Hz)", idContext.getString(R.string.title_eq_config_hz))
        assertEquals("Pengaturan", context.getString(R.string.title_settings))

        val enContext = LocaleHelper.wrapContext(
            context,
            AppConstants.LANG_EN
        )
        assertEquals("Settings", enContext.getString(R.string.title_settings))
        assertEquals("SAVE", enContext.getString(R.string.action_save))
    }
}
