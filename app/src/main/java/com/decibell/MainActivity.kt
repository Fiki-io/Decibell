package com.decibell

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.decibell.ui.screen.EqualizerMainScreen
import com.decibell.ui.theme.DecibellTheme
import com.decibell.util.AppConstants
import com.decibell.util.LocaleHelper
import com.decibell.viewmodel.EqualizerViewModel
import com.decibell.viewmodel.UiFeedbackEvent

class MainActivity : ComponentActivity() {

    private val viewModel: EqualizerViewModel by viewModels {
        EqualizerViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingAudioEffectIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val spectrumBins by viewModel.spectrumBins.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, uiState.eqState.appLanguage) {
                LocaleHelper.wrapContext(baseContext, uiState.eqState.appLanguage)
            }

            var hasRecordPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        baseContext,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val recordPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasRecordPermission = granted
                if (granted) {
                    viewModel.setVisualizerEnabled(true)
                } else {
                    viewModel.onVisualizerPermissionDenied()
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                // Foreground audio engine runs regardless of notification permission
            }

            val exportJsonLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument(AppConstants.MIME_TYPE_JSON)
            ) { uri ->
                if (uri != null) {
                    viewModel.exportBackupToUri(uri)
                }
            }

            val importJsonLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importBackupFromUri(uri)
                }
            }

            // Observe hardware volume changes (STREAM_MUSIC)
            DisposableEffect(baseContext) {
                val volumeReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == AppConstants.VOLUME_CHANGED_ACTION) {
                            viewModel.refreshSystemVolume()
                        }
                    }
                }
                val filter = IntentFilter(AppConstants.VOLUME_CHANGED_ACTION)
                ContextCompat.registerReceiver(
                    baseContext,
                    volumeReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                onDispose {
                    runCatching { baseContext.unregisterReceiver(volumeReceiver) }
                }
            }

            LaunchedEffect(viewModel, localizedContext) {
                viewModel.feedbackEvents.collect { event ->
                    when (event) {
                        is UiFeedbackEvent.MessageRes -> {
                            val message = if (event.formatArgs.isEmpty()) {
                                localizedContext.getString(event.resId)
                            } else {
                                localizedContext.getString(
                                    event.resId,
                                    *event.formatArgs.toTypedArray()
                                )
                            }
                            snackbarHostState.showSnackbar(
                                message = message,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration
            ) {
                DecibellTheme {
                    EqualizerMainScreen(
                        uiState = uiState,
                        spectrumBins = spectrumBins,
                        snackbarHostState = snackbarHostState,
                        hasRecordAudioPermission = hasRecordPermission,
                        onRequestRecordAudioPermission = {
                            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onSetEqEnabled = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val notifGranted = ContextCompat.checkSelfPermission(
                                    baseContext,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!notifGranted) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            }
                            viewModel.setEqEnabled(enabled)
                        },
                        onSelectStandardBandCount = viewModel::selectStandardBandCount,
                        onSelectCustomConfig = viewModel::selectCustomConfig,
                        onSetChannelJoined = viewModel::setChannelJoined,
                        onBandGainChanged = viewModel::updateBandGain,
                        onSelectBuiltInPreset = viewModel::selectBuiltInPreset,
                        onSelectCustomPreset = viewModel::selectCustomPreset,
                        onSaveCustomPreset = viewModel::saveCustomPreset,
                        onDeleteCustomPreset = viewModel::deleteCustomPreset,
                        onSaveCustomFrequencyConfig = viewModel::saveCustomFrequencyConfig,
                        onDeleteCustomFrequencyConfig = viewModel::deleteCustomFrequencyConfig,
                        onSetMaxGainRangeDb = viewModel::setMaxGainRangeDb,
                        onSetSliderSize = viewModel::setSliderSize,
                        onSetVisualizerEnabled = viewModel::setVisualizerEnabled,
                        onSetVisualizerIntervalMs = viewModel::setVisualizerIntervalMs,
                        onSetPreGainDb = viewModel::setPreGainDb,
                        onSetBalanceDb = viewModel::setBalanceDb,
                        onSetOutputVolumePercent = viewModel::setOutputVolumePercent,
                        onSetEffectsExpanded = viewModel::setEffectsExpanded,
                        onSetReverbPreset = viewModel::setReverbPreset,
                        onSetBassBoostStrength = viewModel::setBassBoostStrength,
                        onSetVirtualizerStrength = viewModel::setVirtualizerStrength,
                        onSetAutoPanRateHz = viewModel::setAutoPanRateHz,
                        onSetMbcEnabled = viewModel::setMbcEnabled,
                        onSetMbcPreset = viewModel::setMbcPreset,
                        onUpdateLimiterSettings = viewModel::updateLimiterSettings,
                        onSetGlobalMixFallback = viewModel::setGlobalMixFallback,
                        onSetAppLanguage = viewModel::setAppLanguage,
                        onExportJsonClick = {
                            exportJsonLauncher.launch(AppConstants.DEFAULT_EXPORT_FILENAME)
                        },
                        onImportJsonClick = {
                            importJsonLauncher.launch(arrayOf(AppConstants.MIME_TYPE_JSON, "*/*"))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingAudioEffectIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemVolume()
    }

    private fun handleIncomingAudioEffectIntent(intent: Intent?) {
        if (intent?.action == AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) {
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
            val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                ?: callingPackage.orEmpty()
            if (sessionId > 0) {
                viewModel.onExternalControlPanelSession(sessionId, packageName)
                setResult(RESULT_OK)
            }
        }
    }
}
