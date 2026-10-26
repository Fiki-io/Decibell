package com.decibell.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.decibell.R
import com.decibell.data.local.EqConfigEntity
import com.decibell.data.local.EqPresetEntity
import com.decibell.data.model.BuiltInPresets
import com.decibell.data.model.LimiterSettings
import com.decibell.data.model.MbcPresetType
import com.decibell.data.model.ReverbPresetType
import com.decibell.data.model.SliderSize
import com.decibell.domain.engine.EngineStatus
import com.decibell.ui.components.DecibellActionButton
import com.decibell.ui.components.DecibellSelectionBottomSheet
import com.decibell.ui.components.EqBandGraph
import com.decibell.ui.components.EqFrequencyConfigDialog
import com.decibell.ui.components.InfoIconButton
import com.decibell.ui.components.InfoTopicDialog
import com.decibell.ui.components.LayeredGroupCard
import com.decibell.ui.components.MaxDbRangeDialog
import com.decibell.ui.components.ParameterSliderRow
import com.decibell.ui.components.SavePresetDialog
import com.decibell.ui.components.SettingsBottomSheet
import com.decibell.ui.components.SheetOptionItem
import com.decibell.ui.components.VisualizerPermissionDialog
import com.decibell.ui.theme.DecibellColors
import com.decibell.ui.theme.DecibellDimens
import com.decibell.ui.theme.DecibellIcons
import com.decibell.ui.theme.DecibellMotion
import com.decibell.ui.theme.DecibellShapes
import com.decibell.util.AppConstants
import com.decibell.viewmodel.EqualizerUiState
import com.decibell.viewmodel.InfoTopic
import kotlin.math.roundToInt

@Composable
fun EqualizerMainScreen(
    uiState: EqualizerUiState,
    spectrumBins: FloatArray,
    snackbarHostState: SnackbarHostState,
    hasRecordAudioPermission: Boolean,
    onRequestRecordAudioPermission: () -> Unit,
    onSetEqEnabled: (Boolean) -> Unit,
    onSelectStandardBandCount: (Int) -> Unit,
    onSelectCustomConfig: (EqConfigEntity) -> Unit,
    onSetChannelJoined: (Boolean) -> Unit,
    onBandGainChanged: (Boolean, Int, Float) -> Unit,
    onSelectBuiltInPreset: (String) -> Unit,
    onSelectCustomPreset: (EqPresetEntity) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onSaveCustomFrequencyConfig: (Long, String, Int, Boolean, List<Int>, List<Int>, () -> Unit) -> Unit,
    onDeleteCustomFrequencyConfig: (Long, () -> Unit) -> Unit,
    onSetMaxGainRangeDb: (Int) -> Unit,
    onSetSliderSize: (SliderSize) -> Unit,
    onSetVisualizerEnabled: (Boolean) -> Unit,
    onSetVisualizerIntervalMs: (Int) -> Unit,
    onSetPreGainDb: (Float) -> Unit,
    onSetBalanceDb: (Float) -> Unit,
    onSetOutputVolumePercent: (Int) -> Unit,
    onSetEffectsExpanded: (Boolean) -> Unit,
    onSetReverbPreset: (ReverbPresetType) -> Unit,
    onSetBassBoostStrength: (Int) -> Unit,
    onSetVirtualizerStrength: (Int) -> Unit,
    onSetAutoPanRateHz: (Float) -> Unit,
    onSetMbcEnabled: (Boolean) -> Unit,
    onSetMbcPreset: (MbcPresetType) -> Unit,
    onUpdateLimiterSettings: (LimiterSettings) -> Unit,
    onSetGlobalMixFallback: (Boolean) -> Unit,
    onSetAppLanguage: (String) -> Unit,
    onExportJsonClick: () -> Unit,
    onImportJsonClick: () -> Unit
) {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val haptics = LocalHapticFeedback.current
    val animationsEnabled = remember(context) { DecibellMotion.isAnimationEnabled(context) }
    val state = uiState.eqState

    var showConfigDropdown by remember { mutableStateOf(false) }
    var showChannelDropdown by remember { mutableStateOf(false) }
    var showPresetSheet by remember { mutableStateOf(false) }
    var showSizeSheet by remember { mutableStateOf(false) }
    var showReverbSheet by remember { mutableStateOf(false) }
    var showMbcPresetSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showEqConfigHzDialog by remember { mutableStateOf(false) }
    var showMaxDbDialog by remember { mutableStateOf(false) }
    var showVisualizerPermissionDialog by remember { mutableStateOf(false) }
    var activeInfoTopic by remember { mutableStateOf<InfoTopic?>(null) }
    var isDraggingEqBand by remember { mutableStateOf(false) }
    val mainVerticalScrollState = rememberScrollState()

    BackHandler(enabled = state.effectsExpanded) {
        onSetEffectsExpanded(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DecibellColors.BackgroundPrimary,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DecibellColors.BackgroundTopGradient,
                            DecibellColors.BackgroundPrimary
                        ),
                        startY = 0f,
                        endY = 900f
                    )
                )
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = DecibellDimens.MaxContentWidth)
                    .verticalScroll(
                        state = mainVerticalScrollState,
                        enabled = !isDraggingEqBand
                    )
                    .padding(
                        top = DecibellDimens.SpacingSm,
                        bottom = DecibellDimens.SpacingXl
                    ),
                verticalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingSm)
            ) {
                val statusText = when (val status = uiState.engineStatus) {
                    is EngineStatus.ActiveSession -> stringResource(
                        R.string.status_active_session_format,
                        status.sessionId,
                        status.packageName
                    )
                    is EngineStatus.ActiveGlobalMix -> stringResource(R.string.status_active_global_mix)
                    is EngineStatus.Error -> stringResource(
                        R.string.status_engine_error_format,
                        status.message
                    )
                    EngineStatus.Inactive -> stringResource(R.string.status_press_eq_to_activate)
                }

                // 1. Master Header & Quick Control Strip (Directly on background, no card wrapper)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DecibellDimens.ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXxs)
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                color = DecibellColors.TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(DecibellDimens.StatusDotSize)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.eqEnabled) {
                                                DecibellColors.AccentBlue
                                            } else {
                                                DecibellColors.TextSecondary
                                            }
                                        )
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.eqEnabled) {
                                        DecibellColors.AccentBlue
                                    } else {
                                        DecibellColors.TextSecondary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("engine_status_text")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(DecibellDimens.SpacingSm))

                        // Master EQ Switch + Settings Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                        ) {
                            Text(
                                text = stringResource(R.string.label_eq_on),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DecibellColors.TextPrimary
                            )
                            Switch(
                                checked = state.eqEnabled,
                                onCheckedChange = { enabled ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSetEqEnabled(enabled)
                                },
                                modifier = Modifier.testTag("eq_enable_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DecibellColors.TextPrimary,
                                    checkedTrackColor = DecibellColors.AccentBlue,
                                    uncheckedThumbColor = DecibellColors.TextPrimary,
                                    uncheckedTrackColor = DecibellColors.TrackInactive
                                )
                            )

                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier
                                    .size(DecibellDimens.SettingsButtonSize)
                                    .clip(CircleShape)
                                    .background(DecibellColors.SurfaceInteractive)
                                    .border(
                                        DecibellDimens.BorderThin,
                                        DecibellColors.BorderSubtle,
                                        CircleShape
                                    )
                                    .testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.cd_settings),
                                    tint = DecibellColors.TextPrimary
                                )
                            }
                        }
                    }

                    // Quick Control Strip Row 1: Config Selector Pill & Channel Mode Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                    ) {
                        // Config Selector Pill
                        Box(modifier = Modifier.weight(1f)) {
                            val activeConfigLabel = if (
                                state.activeConfigId != AppConstants.DEFAULT_CONFIG_ID &&
                                state.activeConfigName.isNotBlank()
                            ) {
                                state.activeConfigName
                            } else {
                                state.bandCount.toString()
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clip(DecibellShapes.small)
                                    .background(DecibellColors.SurfaceInteractive)
                                    .border(
                                        DecibellDimens.BorderThin,
                                        DecibellColors.BorderSubtle,
                                        DecibellShapes.small
                                    )
                                    .clickable { showConfigDropdown = true }
                                    .padding(
                                        horizontal = DecibellDimens.SpacingSm,
                                        vertical = DecibellDimens.SpacingXs
                                    )
                                    .testTag("config_selector_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.label_config),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DecibellColors.TextSecondary,
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeConfigLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (state.activeConfigId != AppConstants.DEFAULT_CONFIG_ID) {
                                            DecibellColors.AccentBlue
                                        } else {
                                            DecibellColors.TextPrimary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("active_config_label_text")
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = DecibellColors.TextSecondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showConfigDropdown,
                                onDismissRequest = { showConfigDropdown = false },
                                containerColor = DecibellColors.SurfaceInteractive
                            ) {
                                CompositionLocalProvider(
                                    LocalContext provides context,
                                    LocalConfiguration provides currentConfiguration
                                ) {
                                    AppConstants.MAIN_BAND_COUNTS.forEach { count ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = count.toString(),
                                                    color = DecibellColors.TextPrimary
                                                )
                                            },
                                            onClick = {
                                                onSelectStandardBandCount(count)
                                                showConfigDropdown = false
                                            },
                                            modifier = Modifier.testTag("config_option_$count")
                                        )
                                    }
                                    uiState.customConfigs.forEach { cfg ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${cfg.name} (${cfg.bandCount})",
                                                    color = DecibellColors.AccentBlue
                                                )
                                            },
                                            onClick = {
                                                onSelectCustomConfig(cfg)
                                                showConfigDropdown = false
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = DecibellColors.DividerLine)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.action_edit_hz_config),
                                                color = DecibellColors.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            showConfigDropdown = false
                                            showEqConfigHzDialog = true
                                        },
                                        modifier = Modifier.testTag("open_eq_config_hz_menu_item")
                                    )
                                }
                            }
                        }

                        // Channel Mode Selector Pill
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clip(DecibellShapes.small)
                                    .background(DecibellColors.SurfaceInteractive)
                                    .border(
                                        DecibellDimens.BorderThin,
                                        DecibellColors.BorderSubtle,
                                        DecibellShapes.small
                                    )
                                    .clickable { showChannelDropdown = true }
                                    .padding(
                                        horizontal = DecibellDimens.SpacingSm,
                                        vertical = DecibellDimens.SpacingXs
                                    )
                                    .testTag("channel_mode_selector"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.label_channels),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DecibellColors.TextSecondary,
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (state.isChannelJoined) {
                                            stringResource(R.string.channel_joined)
                                        } else {
                                            stringResource(R.string.channel_separate)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = DecibellColors.TextPrimary,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = DecibellColors.TextSecondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showChannelDropdown,
                                onDismissRequest = { showChannelDropdown = false },
                                containerColor = DecibellColors.SurfaceInteractive
                            ) {
                                CompositionLocalProvider(
                                    LocalContext provides context,
                                    LocalConfiguration provides currentConfiguration
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.channel_joined),
                                                color = DecibellColors.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            onSetChannelJoined(true)
                                            showChannelDropdown = false
                                        },
                                        modifier = Modifier.testTag("channel_option_joined")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.channel_separate),
                                                color = DecibellColors.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            onSetChannelJoined(false)
                                            showChannelDropdown = false
                                        },
                                        modifier = Modifier.testTag("channel_option_separate")
                                    )
                                }
                            }
                        }
                    }

                    // Quick Control Strip Row 2: Preset Selector Pill + Save Action Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(DecibellShapes.small)
                                .background(DecibellColors.SurfaceInteractive)
                                .border(
                                    DecibellDimens.BorderThin,
                                    DecibellColors.BorderSubtle,
                                    DecibellShapes.small
                                )
                                .clickable { showPresetSheet = true }
                                .padding(
                                    horizontal = DecibellDimens.SpacingSm,
                                    vertical = DecibellDimens.SpacingXs
                                )
                                .testTag("preset_selector_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.label_preset),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DecibellColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                                Text(
                                    text = localizedPresetName(state.activePresetName),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DecibellColors.AccentBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = DecibellColors.TextSecondary
                            )
                        }

                        DecibellActionButton(
                            text = if (uiState.isCurrentPresetModified) {
                                stringResource(R.string.action_save_modified)
                            } else {
                                stringResource(R.string.action_save)
                            },
                            onClick = { showSavePresetDialog = true },
                            isPrimary = uiState.isCurrentPresetModified,
                            modifier = Modifier.width(DecibellDimens.ConfigInputWidth),
                            testTagName = "save_preset_button"
                        )
                    }
                }

                // 2. Equalizer Band Graph Area (Single Joined or Dual Separate Left + Right)
                if (state.isChannelJoined) {
                    EqBandGraph(
                        gainsDb = state.leftGainsDb,
                        cutoffsHz = state.leftCutoffsHz,
                        maxGainRangeDb = state.maxGainRangeDb,
                        sliderSize = state.sliderSize,
                        visualizerEnabled = state.visualizerEnabled && state.eqEnabled,
                        spectrumBins = spectrumBins,
                        onBandGainChanged = { bandIdx, newGain ->
                            onBandGainChanged(true, bandIdx, newGain)
                        },
                        onScaleClick = { showMaxDbDialog = true },
                        onDragActiveChanged = { isDraggingEqBand = it },
                        channelTitle = null,
                        graphTestTag = "eq_graph_joined"
                    )
                } else {
                    EqBandGraph(
                        gainsDb = state.leftGainsDb,
                        cutoffsHz = state.leftCutoffsHz,
                        maxGainRangeDb = state.maxGainRangeDb,
                        sliderSize = state.sliderSize,
                        visualizerEnabled = state.visualizerEnabled && state.eqEnabled,
                        spectrumBins = spectrumBins,
                        onBandGainChanged = { bandIdx, newGain ->
                            onBandGainChanged(true, bandIdx, newGain)
                        },
                        onScaleClick = { showMaxDbDialog = true },
                        onDragActiveChanged = { isDraggingEqBand = it },
                        channelTitle = stringResource(R.string.channel_left),
                        graphTestTag = "eq_graph_left"
                    )
                    EqBandGraph(
                        gainsDb = state.rightGainsDb,
                        cutoffsHz = state.rightCutoffsHz,
                        maxGainRangeDb = state.maxGainRangeDb,
                        sliderSize = state.sliderSize,
                        visualizerEnabled = state.visualizerEnabled && state.eqEnabled,
                        spectrumBins = spectrumBins,
                        onBandGainChanged = { bandIdx, newGain ->
                            onBandGainChanged(false, bandIdx, newGain)
                        },
                        onScaleClick = { showMaxDbDialog = true },
                        onDragActiveChanged = { isDraggingEqBand = it },
                        channelTitle = stringResource(R.string.channel_right),
                        graphTestTag = "eq_graph_right"
                    )
                }

                // 3. Unified Studio Control Panel (Display, Gain/Balance/Output, & Expandable DSP Effects)
                LayeredGroupCard(
                    modifier = Modifier.padding(horizontal = DecibellDimens.ScreenHorizontalPadding)
                ) {
                    // Row A: Fader Size & Visualizer Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(DecibellShapes.extraSmall)
                                    .background(DecibellColors.SurfaceInteractive)
                                    .border(
                                        DecibellDimens.BorderThin,
                                        DecibellColors.BorderSubtle,
                                        DecibellShapes.extraSmall
                                    )
                                    .clickable { showSizeSheet = true }
                                    .padding(
                                        horizontal = DecibellDimens.SpacingSm,
                                        vertical = DecibellDimens.SpacingXxs
                                    )
                                    .testTag("slider_size_selector"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.label_size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DecibellColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                                Text(
                                    text = when (state.sliderSize) {
                                        SliderSize.SMALL -> stringResource(R.string.size_small)
                                        SliderSize.MEDIUM -> stringResource(R.string.size_medium)
                                        SliderSize.LARGE -> stringResource(R.string.size_large)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DecibellColors.AccentBlue
                                )
                            }
                            InfoIconButton(
                                onClick = { activeInfoTopic = InfoTopic.SIZE },
                                testTagName = "info_size_button"
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.label_visualizer),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DecibellColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                            Switch(
                                checked = state.visualizerEnabled,
                                onCheckedChange = { wantEnabled ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (wantEnabled && !hasRecordAudioPermission) {
                                        showVisualizerPermissionDialog = true
                                    } else {
                                        onSetVisualizerEnabled(wantEnabled)
                                    }
                                },
                                modifier = Modifier.testTag("visualizer_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DecibellColors.TextPrimary,
                                    checkedTrackColor = DecibellColors.AccentBlue,
                                    uncheckedThumbColor = DecibellColors.TextPrimary,
                                    uncheckedTrackColor = DecibellColors.TrackInactive
                                )
                            )
                            InfoIconButton(
                                onClick = { activeInfoTopic = InfoTopic.VISUALIZER },
                                testTagName = "info_visualizer_button"
                            )
                        }
                    }

                    // Visualizer Refresh Interval Slider Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DecibellDimens.SpacingXxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = state.visualizerIntervalMs.toFloat(),
                            onValueChange = { onSetVisualizerIntervalMs(it.roundToInt()) },
                            valueRange = AppConstants.MIN_VISUALIZER_MS.toFloat()..AppConstants.MAX_VISUALIZER_MS.toFloat(),
                            enabled = state.visualizerEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = DecibellDimens.SpacingXs)
                                .testTag("visualizer_interval_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = DecibellColors.AccentBlue,
                                activeTrackColor = DecibellColors.AccentBlue,
                                inactiveTrackColor = DecibellColors.TrackInactive
                            )
                        )
                        Text(
                            text = stringResource(R.string.value_ms_format, state.visualizerIntervalMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.visualizerEnabled) {
                                DecibellColors.TextPrimary
                            } else {
                                DecibellColors.TextSecondary
                            }
                        )
                    }

                    HorizontalDivider(
                        thickness = DecibellDimens.DividerThickness,
                        color = DecibellColors.DividerLine
                    )

                    val maxDbInt = state.maxGainRangeDb.roundToInt()
                    // Pre-gain Slider (scales with maxGainRangeDb and snaps with haptic at 0 dB)
                    ParameterSliderRow(
                        label = stringResource(R.string.label_pre_gain),
                        valueText = stringResource(R.string.value_db_format, state.preGainDb),
                        value = state.preGainDb,
                        valueRange = (-state.maxGainRangeDb)..state.maxGainRangeDb,
                        onValueChange = onSetPreGainDb,
                        snapToZeroDetent = true,
                        leftScaleLabel = stringResource(R.string.scale_minus_db_format, maxDbInt),
                        rightScaleLabel = stringResource(R.string.scale_plus_db_format, maxDbInt),
                        sliderTestTag = "pre_gain_slider"
                    )

                    HorizontalDivider(
                        thickness = DecibellDimens.DividerThickness,
                        color = DecibellColors.DividerLine
                    )

                    // Balance Slider (snaps with haptic at 0 dB center)
                    ParameterSliderRow(
                        label = stringResource(R.string.label_balance),
                        valueText = stringResource(
                            R.string.value_db_int_format,
                            state.balanceDb.roundToInt()
                        ),
                        value = state.balanceDb,
                        valueRange = (-AppConstants.DEFAULT_BALANCE_RANGE_DB)..AppConstants.DEFAULT_BALANCE_RANGE_DB,
                        onValueChange = onSetBalanceDb,
                        snapToZeroDetent = true,
                        leftScaleLabel = stringResource(R.string.scale_left_short),
                        rightScaleLabel = stringResource(R.string.scale_right_short),
                        sliderTestTag = "balance_slider"
                    )

                    HorizontalDivider(
                        thickness = DecibellDimens.DividerThickness,
                        color = DecibellColors.DividerLine
                    )

                    // Output Volume Slider
                    ParameterSliderRow(
                        label = stringResource(R.string.label_output),
                        valueText = stringResource(
                            R.string.value_percent_format,
                            state.outputVolumePercent
                        ),
                        value = state.outputVolumePercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { onSetOutputVolumePercent(it.roundToInt()) },
                        sliderTestTag = "output_volume_slider"
                    )

                    HorizontalDivider(
                        thickness = DecibellDimens.DividerThickness,
                        color = DecibellColors.DividerLine
                    )

                    // Expandable Effects Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DecibellDimens.RowHeightCompact)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetEffectsExpanded(!state.effectsExpanded)
                            }
                            .testTag("effects_expand_header"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.label_effects),
                            style = MaterialTheme.typography.titleMedium,
                            color = DecibellColors.TextPrimary
                        )
                        Icon(
                            imageVector = if (state.effectsExpanded) {
                                DecibellIcons.ArrowDropUp
                            } else {
                                Icons.Default.ArrowDropDown
                            },
                            contentDescription = null,
                            tint = DecibellColors.AccentBlue
                        )
                    }

                    // Expandable Effects Content (Divided cleanly by thin HorizontalDividers, no nested cards)
                    AnimatedVisibility(
                        visible = state.effectsExpanded,
                        enter = expandVertically(
                            animationSpec = DecibellMotion.standardTween(animationsEnabled)
                        ) + fadeIn(animationSpec = DecibellMotion.standardTween(animationsEnabled)),
                        exit = shrinkVertically(
                            animationSpec = DecibellMotion.fastTween(animationsEnabled)
                        ) + fadeOut(animationSpec = DecibellMotion.fastTween(animationsEnabled))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                        ) {
                            HorizontalDivider(
                                thickness = DecibellDimens.DividerThickness,
                                color = DecibellColors.DividerLine
                            )

                            // Reverb Row Pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(DecibellDimens.RowHeightCompact)
                                    .clip(DecibellShapes.extraSmall)
                                    .background(DecibellColors.SurfaceInteractive)
                                    .border(
                                        DecibellDimens.BorderThin,
                                        DecibellColors.BorderSubtle,
                                        DecibellShapes.extraSmall
                                    )
                                    .clickable { showReverbSheet = true }
                                    .padding(horizontal = DecibellDimens.SpacingSm)
                                    .testTag("reverb_selector_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.label_reverb),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DecibellColors.TextSecondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = localizedReverbName(state.reverbPreset),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = DecibellColors.AccentBlue
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = DecibellColors.TextSecondary
                                    )
                                }
                            }

                            // Bass Boost Slider
                            ParameterSliderRow(
                                label = stringResource(R.string.label_bass_boost),
                                valueText = if (state.bassBoostStrength == 0) {
                                    stringResource(R.string.value_off)
                                } else {
                                    stringResource(
                                        R.string.value_percent_format,
                                        (state.bassBoostStrength / 10).coerceAtLeast(1)
                                    )
                                },
                                value = state.bassBoostStrength.toFloat(),
                                valueRange = AppConstants.MIN_EFFECT_STRENGTH.toFloat()..AppConstants.MAX_EFFECT_STRENGTH.toFloat(),
                                onValueChange = { onSetBassBoostStrength(it.roundToInt()) },
                                onInfoClick = { activeInfoTopic = InfoTopic.BASS_BOOST },
                                sliderTestTag = "bass_boost_slider"
                            )

                            HorizontalDivider(
                                thickness = DecibellDimens.DividerThickness,
                                color = DecibellColors.DividerLine
                            )

                            // 3D Sound Virtualizer Slider
                            ParameterSliderRow(
                                label = stringResource(R.string.label_3d_sound),
                                valueText = if (state.virtualizerStrength == 0) {
                                    stringResource(R.string.value_off)
                                } else {
                                    stringResource(
                                        R.string.value_percent_format,
                                        (state.virtualizerStrength / 10).coerceAtLeast(1)
                                    )
                                },
                                value = state.virtualizerStrength.toFloat(),
                                valueRange = AppConstants.MIN_EFFECT_STRENGTH.toFloat()..AppConstants.MAX_EFFECT_STRENGTH.toFloat(),
                                onValueChange = { onSetVirtualizerStrength(it.roundToInt()) },
                                onInfoClick = { activeInfoTopic = InfoTopic.SOUND_3D },
                                sliderTestTag = "virtualizer_3d_slider"
                            )

                            HorizontalDivider(
                                thickness = DecibellDimens.DividerThickness,
                                color = DecibellColors.DividerLine
                            )

                            // Auto-pan Slider
                            ParameterSliderRow(
                                label = stringResource(R.string.label_auto_pan),
                                valueText = if (state.autoPanRateHz <= 0.02f) {
                                    stringResource(R.string.value_off)
                                } else {
                                    stringResource(R.string.value_hz_rate_format, state.autoPanRateHz)
                                },
                                value = state.autoPanRateHz,
                                valueRange = AppConstants.MIN_AUTO_PAN_HZ..AppConstants.MAX_AUTO_PAN_HZ,
                                onValueChange = onSetAutoPanRateHz,
                                onInfoClick = { activeInfoTopic = InfoTopic.AUTO_PAN },
                                sliderTestTag = "auto_pan_slider"
                            )

                            HorizontalDivider(
                                thickness = DecibellDimens.DividerThickness,
                                color = DecibellColors.DividerLine
                            )

                            // Multi-band Compressor Section (Flat inside parent card, no nested sub-card)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.mbcEnabled,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSetMbcEnabled(it)
                                    },
                                    modifier = Modifier.testTag("mbc_checkbox"),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DecibellColors.AccentBlue,
                                        uncheckedColor = DecibellColors.TextSecondary,
                                        checkmarkColor = DecibellColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.label_multiband_compressor),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DecibellColors.TextPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSetMbcEnabled(!state.mbcEnabled)
                                        }
                                )
                                InfoIconButton(
                                    onClick = { activeInfoTopic = InfoTopic.MBC },
                                    testTagName = "info_mbc_button"
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clip(DecibellShapes.extraSmall)
                                    .background(DecibellColors.SurfaceInteractive)
                                    .border(
                                        DecibellDimens.BorderThin,
                                        DecibellColors.BorderSubtle,
                                        DecibellShapes.extraSmall
                                    )
                                    .clickable { showMbcPresetSheet = true }
                                    .padding(horizontal = DecibellDimens.SpacingSm)
                                    .testTag("mbc_preset_selector"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.label_preset),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DecibellColors.TextSecondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = localizedMbcPresetName(state.mbcPreset),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = DecibellColors.AccentBlue
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = DecibellColors.TextSecondary
                                    )
                                }
                            }

                            HorizontalDivider(
                                thickness = DecibellDimens.DividerThickness,
                                color = DecibellColors.DividerLine
                            )

                            // Limiter Section (Flat inside parent card, no nested sub-card)
                            val lim = state.limiterSettings
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = lim.enabled,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateLimiterSettings(lim.copy(enabled = it))
                                    },
                                    modifier = Modifier.testTag("limiter_checkbox"),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DecibellColors.AccentBlue,
                                        uncheckedColor = DecibellColors.TextSecondary,
                                        checkmarkColor = DecibellColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.label_limiter),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DecibellColors.TextPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onUpdateLimiterSettings(lim.copy(enabled = !lim.enabled))
                                        }
                                )
                                InfoIconButton(
                                    onClick = { activeInfoTopic = InfoTopic.LIMITER },
                                    testTagName = "info_limiter_button"
                                )
                            }

                            ParameterSliderRow(
                                label = stringResource(R.string.label_attack_time),
                                valueText = stringResource(
                                    R.string.value_ms_format,
                                    lim.attackTimeMs.roundToInt()
                                ),
                                value = lim.attackTimeMs,
                                valueRange = AppConstants.MIN_LIMITER_ATTACK_MS..AppConstants.MAX_LIMITER_ATTACK_MS,
                                enabled = lim.enabled,
                                onValueChange = {
                                    onUpdateLimiterSettings(lim.copy(attackTimeMs = it))
                                },
                                sliderTestTag = "limiter_attack_slider"
                            )

                            ParameterSliderRow(
                                label = stringResource(R.string.label_release_time),
                                valueText = stringResource(
                                    R.string.value_ms_format,
                                    lim.releaseTimeMs.roundToInt()
                                ),
                                value = lim.releaseTimeMs,
                                valueRange = AppConstants.MIN_LIMITER_RELEASE_MS..AppConstants.MAX_LIMITER_RELEASE_MS,
                                enabled = lim.enabled,
                                onValueChange = {
                                    onUpdateLimiterSettings(lim.copy(releaseTimeMs = it))
                                },
                                sliderTestTag = "limiter_release_slider"
                            )

                            ParameterSliderRow(
                                label = stringResource(R.string.label_ratio),
                                valueText = stringResource(R.string.value_ratio_format, lim.ratio),
                                value = lim.ratio,
                                valueRange = AppConstants.MIN_LIMITER_RATIO..AppConstants.MAX_LIMITER_RATIO,
                                enabled = lim.enabled,
                                onValueChange = {
                                    onUpdateLimiterSettings(lim.copy(ratio = it))
                                },
                                sliderTestTag = "limiter_ratio_slider"
                            )

                            ParameterSliderRow(
                                label = stringResource(R.string.label_threshold),
                                valueText = stringResource(
                                    R.string.value_db_format,
                                    lim.thresholdDb
                                ),
                                value = lim.thresholdDb,
                                valueRange = AppConstants.MIN_LIMITER_THRESHOLD_DB..AppConstants.MAX_LIMITER_THRESHOLD_DB,
                                enabled = lim.enabled,
                                onValueChange = {
                                    onUpdateLimiterSettings(lim.copy(thresholdDb = it))
                                },
                                sliderTestTag = "limiter_threshold_slider"
                            )

                            ParameterSliderRow(
                                label = stringResource(R.string.label_post_gain),
                                valueText = stringResource(
                                    R.string.value_db_format,
                                    lim.postGainDb
                                ),
                                value = lim.postGainDb,
                                valueRange = AppConstants.MIN_LIMITER_POST_GAIN_DB..AppConstants.MAX_LIMITER_POST_GAIN_DB,
                                enabled = lim.enabled,
                                snapToZeroDetent = true,
                                onValueChange = {
                                    onUpdateLimiterSettings(lim.copy(postGainDb = it))
                                },
                                sliderTestTag = "limiter_post_gain_slider"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMaxDbDialog) {
        MaxDbRangeDialog(
            currentMaxDb = state.maxGainRangeDb.roundToInt(),
            onSaveMaxDb = onSetMaxGainRangeDb,
            onDismiss = { showMaxDbDialog = false }
        )
    }

    // Bottom Sheets & Modal Dialogs
    if (showPresetSheet) {
        val builtInOptions = BuiltInPresets.ALL_NAMES.map { name ->
            SheetOptionItem(
                value = "builtin:$name",
                label = localizedPresetName(name)
            )
        }
        val customOptions = uiState.customPresets.map { entity ->
            SheetOptionItem(
                value = "custom:${entity.name}",
                label = entity.name,
                subtitle = "${entity.bandCount} Bands"
            )
        }
        val currentKey = if (uiState.customPresets.any { it.name == state.activePresetName }) {
            "custom:${state.activePresetName}"
        } else {
            "builtin:${state.activePresetName}"
        }

        DecibellSelectionBottomSheet(
            title = stringResource(R.string.label_preset).removeSuffix(":"),
            options = builtInOptions + customOptions,
            selectedValue = currentKey,
            onSelect = { chosen ->
                if (chosen.startsWith("custom:")) {
                    val name = chosen.removePrefix("custom:")
                    uiState.customPresets.firstOrNull { it.name == name }?.let(onSelectCustomPreset)
                } else {
                    val name = chosen.removePrefix("builtin:")
                    onSelectBuiltInPreset(name)
                }
            },
            onDismiss = { showPresetSheet = false },
            headerActionText = stringResource(R.string.action_reset_flat),
            onHeaderActionClick = { onSelectBuiltInPreset(AppConstants.DEFAULT_PRESET_FLAT) }
        )
    }

    if (showSizeSheet) {
        DecibellSelectionBottomSheet(
            title = stringResource(R.string.info_title_size),
            options = listOf(
                SheetOptionItem(SliderSize.SMALL, stringResource(R.string.size_small)),
                SheetOptionItem(SliderSize.MEDIUM, stringResource(R.string.size_medium)),
                SheetOptionItem(SliderSize.LARGE, stringResource(R.string.size_large))
            ),
            selectedValue = state.sliderSize,
            onSelect = onSetSliderSize,
            onDismiss = { showSizeSheet = false }
        )
    }

    if (showReverbSheet) {
        DecibellSelectionBottomSheet(
            title = stringResource(R.string.label_reverb).removeSuffix(":"),
            options = ReverbPresetType.entries.map { rev ->
                SheetOptionItem(rev, localizedReverbName(rev))
            },
            selectedValue = state.reverbPreset,
            onSelect = onSetReverbPreset,
            onDismiss = { showReverbSheet = false }
        )
    }

    if (showMbcPresetSheet) {
        DecibellSelectionBottomSheet(
            title = stringResource(R.string.label_multiband_compressor),
            options = MbcPresetType.entries.map { mbc ->
                SheetOptionItem(mbc, localizedMbcPresetName(mbc))
            },
            selectedValue = state.mbcPreset,
            onSelect = onSetMbcPreset,
            onDismiss = { showMbcPresetSheet = false }
        )
    }

    if (showLanguageSheet) {
        DecibellSelectionBottomSheet(
            title = stringResource(R.string.label_language),
            options = listOf(
                SheetOptionItem(AppConstants.LANG_SYSTEM, stringResource(R.string.lang_system)),
                SheetOptionItem(AppConstants.LANG_ID, stringResource(R.string.lang_indonesian)),
                SheetOptionItem(AppConstants.LANG_EN, stringResource(R.string.lang_english)),
                SheetOptionItem(AppConstants.LANG_ES, stringResource(R.string.lang_spanish)),
                SheetOptionItem(AppConstants.LANG_JA, stringResource(R.string.lang_japanese))
            ),
            selectedValue = state.appLanguage,
            onSelect = onSetAppLanguage,
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            currentLanguage = state.appLanguage,
            globalMixFallback = state.globalMixFallback,
            activeSessions = uiState.activeSessions,
            onOpenLanguageSelector = { showLanguageSheet = true },
            onOpenEqConfigHz = { showEqConfigHzDialog = true },
            onToggleGlobalMixFallback = onSetGlobalMixFallback,
            onExportJsonClick = onExportJsonClick,
            onImportJsonClick = onImportJsonClick,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showSavePresetDialog) {
        val isCustomSelected = uiState.customPresets.any { it.name == state.activePresetName }
        val suggestedName = if (isCustomSelected) state.activePresetName else ""
        SavePresetDialog(
            initialName = suggestedName,
            canDeleteCurrent = isCustomSelected,
            onSave = onSaveCustomPreset,
            onDeleteCurrent = { onDeleteCustomPreset(state.activePresetName) },
            onDismiss = { showSavePresetDialog = false }
        )
    }

    if (showEqConfigHzDialog) {
        EqFrequencyConfigDialog(
            initialBandCount = state.bandCount,
            initialIsDualChannel = state.isConfigDualChannel,
            initialLeftCutoffs = state.leftCutoffsHz,
            initialRightCutoffs = state.rightCutoffsHz,
            activeConfigId = state.activeConfigId,
            activeConfigName = state.activeConfigName,
            customConfigs = uiState.customConfigs,
            onInfoClick = { activeInfoTopic = InfoTopic.EQ_CONFIG },
            onSaveConfig = { id, name, count, isDual, leftHz, rightHz ->
                onSaveCustomFrequencyConfig(id, name, count, isDual, leftHz, rightHz) {
                    showEqConfigHzDialog = false
                }
            },
            onDeleteConfig = { id ->
                onDeleteCustomFrequencyConfig(id) {
                    showEqConfigHzDialog = false
                }
            },
            onDismiss = { showEqConfigHzDialog = false }
        )
    }

    if (showVisualizerPermissionDialog) {
        VisualizerPermissionDialog(
            onGrantClick = onRequestRecordAudioPermission,
            onDismiss = { showVisualizerPermissionDialog = false }
        )
    }

    activeInfoTopic?.let { topic ->
        InfoTopicDialog(
            topic = topic,
            onDismiss = { activeInfoTopic = null }
        )
    }
}

@Composable
private fun localizedPresetName(presetKey: String): String {
    return when (presetKey) {
        "Flat" -> stringResource(R.string.preset_flat)
        "Custom" -> stringResource(R.string.preset_custom)
        "Bass" -> stringResource(R.string.preset_bass_boost)
        "Acoustic" -> stringResource(R.string.preset_acoustic)
        "Classical" -> stringResource(R.string.preset_classical)
        "Dance" -> stringResource(R.string.preset_dance)
        "Electronic" -> stringResource(R.string.preset_electronic)
        "Hip-Hop" -> stringResource(R.string.preset_hip_hop)
        "Jazz" -> stringResource(R.string.preset_jazz)
        "Pop" -> stringResource(R.string.preset_pop)
        "Rock" -> stringResource(R.string.preset_rock)
        "Treble" -> stringResource(R.string.preset_treble)
        "Vocal" -> stringResource(R.string.preset_vocal)
        else -> presetKey
    }
}

@Composable
private fun localizedReverbName(preset: ReverbPresetType): String {
    return when (preset) {
        ReverbPresetType.NONE -> stringResource(R.string.reverb_none)
        ReverbPresetType.SMALL_ROOM -> stringResource(R.string.reverb_small_room)
        ReverbPresetType.MEDIUM_ROOM -> stringResource(R.string.reverb_medium_room)
        ReverbPresetType.LARGE_ROOM -> stringResource(R.string.reverb_large_room)
        ReverbPresetType.MEDIUM_HALL -> stringResource(R.string.reverb_medium_hall)
        ReverbPresetType.LARGE_HALL -> stringResource(R.string.reverb_large_hall)
        ReverbPresetType.PLATE -> stringResource(R.string.reverb_plate)
    }
}

@Composable
private fun localizedMbcPresetName(preset: MbcPresetType): String {
    return when (preset) {
        MbcPresetType.SPEAKER_BASS_GUARD -> stringResource(R.string.mbc_speaker_bass_guard)
        MbcPresetType.VOCAL_PRESENCE -> stringResource(R.string.mbc_vocal_presence)
        MbcPresetType.WARM_BROADCAST -> stringResource(R.string.mbc_warm_broadcast)
        MbcPresetType.GENTLE_MASTER -> stringResource(R.string.mbc_gentle_master)
        MbcPresetType.PUNCHY_DYNAMICS -> stringResource(R.string.mbc_punchy_dynamics)
    }
}
