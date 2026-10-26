package com.decibell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.decibell.R
import com.decibell.data.model.ActiveAudioSession
import com.decibell.ui.theme.DecibellColors
import com.decibell.ui.theme.DecibellDimens
import com.decibell.ui.theme.DecibellIcons
import com.decibell.ui.theme.DecibellShapes
import com.decibell.util.AppConstants
import com.decibell.viewmodel.InfoTopic

@Composable
fun SavePresetDialog(
    initialName: String,
    canDeleteCurrent: Boolean,
    onSave: (String) -> Unit,
    onDeleteCurrent: () -> Unit,
    onDismiss: () -> Unit
) {
    var presetName by remember { mutableStateOf(initialName) }

    DecibellModalDialog(
        title = stringResource(R.string.title_save_preset),
        description = stringResource(R.string.desc_save_preset),
        confirmText = stringResource(R.string.action_save),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = {
            onSave(presetName)
            onDismiss()
        },
        onDismiss = onDismiss
    ) {
        BasicTextField(
            value = presetName,
            onValueChange = { presetName = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = DecibellColors.TextPrimary
            ),
            cursorBrush = SolidColor(DecibellColors.AccentBlue),
            modifier = Modifier
                .fillMaxWidth()
                .background(DecibellColors.BackgroundPrimary, DecibellShapes.extraSmall)
                .border(
                    DecibellDimens.BorderThin,
                    DecibellColors.BorderFocused,
                    DecibellShapes.extraSmall
                )
                .padding(
                    horizontal = DecibellDimens.SpacingMd,
                    vertical = DecibellDimens.SpacingSm
                )
                .testTag("save_preset_name_input"),
            decorationBox = { innerTextField ->
                Box {
                    if (presetName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_preset_name),
                            style = MaterialTheme.typography.bodyLarge,
                            color = DecibellColors.TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (canDeleteCurrent) {
            Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))
            DecibellActionButton(
                text = stringResource(R.string.action_delete),
                onClick = {
                    onDeleteCurrent()
                    onDismiss()
                },
                isPrimary = false,
                modifier = Modifier.fillMaxWidth(),
                testTagName = "delete_custom_preset_button"
            )
        }
    }
}

@Composable
fun InfoTopicDialog(
    topic: InfoTopic,
    onDismiss: () -> Unit
) {
    DecibellModalDialog(
        title = stringResource(topic.titleRes),
        description = stringResource(topic.descRes),
        confirmText = stringResource(R.string.action_close),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = onDismiss,
        onDismiss = onDismiss
    )
}

@Composable
fun MaxDbRangeDialog(
    currentMaxDb: Int,
    onSaveMaxDb: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var dbInput by remember { mutableStateOf(currentMaxDb.toString()) }

    DecibellModalDialog(
        title = stringResource(R.string.title_db_range_config),
        description = stringResource(R.string.desc_db_range_config),
        confirmText = stringResource(R.string.action_save),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = {
            val parsed = dbInput.trim().toIntOrNull() ?: currentMaxDb
            onSaveMaxDb(parsed)
            onDismiss()
        },
        onDismiss = onDismiss
    ) {
        BasicTextField(
            value = dbInput,
            onValueChange = { text ->
                dbInput = text.filter { it.isDigit() }.take(2)
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = DecibellColors.TextPrimary
            ),
            cursorBrush = SolidColor(DecibellColors.AccentBlue),
            modifier = Modifier
                .fillMaxWidth()
                .background(DecibellColors.BackgroundPrimary, DecibellShapes.extraSmall)
                .border(
                    DecibellDimens.BorderThin,
                    DecibellColors.BorderFocused,
                    DecibellShapes.extraSmall
                )
                .padding(
                    horizontal = DecibellDimens.SpacingMd,
                    vertical = DecibellDimens.SpacingSm
                )
                .testTag("max_db_range_input"),
            decorationBox = { innerTextField ->
                Box {
                    if (dbInput.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_db_range_value),
                            style = MaterialTheme.typography.bodyLarge,
                            color = DecibellColors.TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
        ) {
            AppConstants.QUICK_DB_RANGES.forEach { presetDb ->
                val isSelected = dbInput.toIntOrNull() == presetDb
                DecibellActionButton(
                    text = "$presetDb",
                    onClick = { dbInput = presetDb.toString() },
                    isPrimary = isSelected,
                    modifier = Modifier.weight(1f),
                    testTagName = "quick_db_option_$presetDb"
                )
            }
        }
    }
}

@Composable
fun VisualizerPermissionDialog(
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit
) {
    DecibellModalDialog(
        title = stringResource(R.string.title_permission_visualizer),
        description = stringResource(R.string.desc_permission_visualizer),
        confirmText = stringResource(R.string.action_grant_permission),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = {
            onDismiss()
            onGrantClick()
        },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentLanguage: String,
    globalMixFallback: Boolean,
    activeSessions: List<ActiveAudioSession>,
    onOpenLanguageSelector: () -> Unit,
    onOpenEqConfigHz: () -> Unit,
    onToggleGlobalMixFallback: (Boolean) -> Unit,
    onExportJsonClick: () -> Unit,
    onImportJsonClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val parentContext = LocalContext.current
    val parentConfiguration = LocalConfiguration.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    val languageDisplay = when (currentLanguage) {
        AppConstants.LANG_EN -> stringResource(R.string.lang_english)
        AppConstants.LANG_ID -> stringResource(R.string.lang_indonesian)
        AppConstants.LANG_ES -> stringResource(R.string.lang_spanish)
        AppConstants.LANG_JA -> stringResource(R.string.lang_japanese)
        else -> stringResource(R.string.lang_system)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = DecibellShapes.large,
        containerColor = DecibellColors.SurfaceModal,
        scrimColor = DecibellColors.ScrimDark,
        dragHandle = null
    ) {
        CompositionLocalProvider(
            LocalContext provides parentContext,
            LocalConfiguration provides parentConfiguration
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(DecibellColors.RadialGlowTop, Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = DecibellDimens.RadialGlowRadius.toPx()
                        ),
                        radius = DecibellDimens.RadialGlowRadius.toPx(),
                        center = Offset(size.width / 2f, 0f)
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = DecibellDimens.ScreenHorizontalPadding,
                    vertical = DecibellDimens.SpacingMd
                )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.title_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = DecibellColors.TextPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(DecibellDimens.MinTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = DecibellColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))

            // Section 1: General
            SectionTitle(text = stringResource(R.string.section_general))
            LayeredGroupCard {
                SettingsNavigationRow(
                    icon = DecibellIcons.Language,
                    label = stringResource(R.string.label_language),
                    valueText = languageDisplay,
                    onClick = {
                        onDismiss()
                        onOpenLanguageSelector()
                    },
                    testTagName = "settings_language_row"
                )
                HorizontalDivider(
                    thickness = DecibellDimens.DividerThickness,
                    color = DecibellColors.DividerLine
                )
                SettingsNavigationRow(
                    icon = DecibellIcons.Tune,
                    label = stringResource(R.string.action_edit_hz_config),
                    valueText = "",
                    onClick = {
                        onDismiss()
                        onOpenEqConfigHz()
                    },
                    testTagName = "settings_edit_hz_row"
                )
            }

            Spacer(modifier = Modifier.height(DecibellDimens.SpacingMd))

            // Section 2: Audio Routing & Sessions
            SectionTitle(text = stringResource(R.string.section_audio_session))
            LayeredGroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DecibellDimens.RowHeightTwoLine)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleGlobalMixFallback(!globalMixFallback)
                        }
                        .testTag("settings_global_mix_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_global_mix_fallback),
                            style = MaterialTheme.typography.bodyLarge,
                            color = DecibellColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.desc_global_mix_fallback),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DecibellColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(DecibellDimens.SpacingSm))
                    Switch(
                        checked = globalMixFallback,
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleGlobalMixFallback(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DecibellColors.TextPrimary,
                            checkedTrackColor = DecibellColors.AccentBlue,
                            uncheckedThumbColor = DecibellColors.TextPrimary,
                            uncheckedTrackColor = DecibellColors.TrackInactive
                        )
                    )
                }

                HorizontalDivider(
                    thickness = DecibellDimens.DividerThickness,
                    color = DecibellColors.DividerLine
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = DecibellDimens.SpacingSm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = DecibellIcons.GraphicEq,
                            contentDescription = null,
                            tint = DecibellColors.TextSecondary,
                            modifier = Modifier.size(DecibellDimens.IconSizeStandard)
                        )
                        Spacer(modifier = Modifier.width(DecibellDimens.SpacingSm))
                        Text(
                            text = stringResource(R.string.label_active_sessions),
                            style = MaterialTheme.typography.bodyLarge,
                            color = DecibellColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(DecibellDimens.SpacingXs))
                    if (activeSessions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.desc_no_player_sessions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DecibellColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        activeSessions.forEach { session ->
                            Text(
                                text = "#${session.sessionId} - ${session.packageName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DecibellColors.AccentBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(DecibellDimens.SpacingMd))

            // Section 3: Preset Backup (JSON)
            SectionTitle(text = stringResource(R.string.section_backup))
            LayeredGroupCard {
                SettingsNavigationRow(
                    icon = DecibellIcons.FileUpload,
                    label = stringResource(R.string.action_export_json),
                    valueText = "",
                    onClick = {
                        onDismiss()
                        onExportJsonClick()
                    },
                    testTagName = "settings_export_json_row"
                )
                HorizontalDivider(
                    thickness = DecibellDimens.DividerThickness,
                    color = DecibellColors.DividerLine
                )
                SettingsNavigationRow(
                    icon = DecibellIcons.FileDownload,
                    label = stringResource(R.string.action_import_json),
                    valueText = "",
                    onClick = {
                        onDismiss()
                        onImportJsonClick()
                    },
                    testTagName = "settings_import_json_row"
                )
            }

            Spacer(modifier = Modifier.height(DecibellDimens.SpacingXl))
        }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = DecibellColors.TextSecondary,
        modifier = Modifier.padding(
            start = DecibellDimens.SpacingXs,
            bottom = DecibellDimens.SpacingXs
        )
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    onClick: () -> Unit,
    testTagName: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DecibellDimens.RowHeightStandard)
            .clickable(onClick = onClick)
            .let { if (testTagName.isNotEmpty()) it.testTag(testTagName) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DecibellColors.TextSecondary,
            modifier = Modifier.size(DecibellDimens.IconSizeStandard)
        )
        Spacer(modifier = Modifier.width(DecibellDimens.SpacingSm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = DecibellColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = DecibellColors.TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(DecibellDimens.SpacingXxs))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = DecibellColors.TextSecondary,
            modifier = Modifier.size(DecibellDimens.IconSizeStandard)
        )
    }
}
