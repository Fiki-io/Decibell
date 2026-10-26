package com.decibell.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decibell.R
import com.decibell.data.local.EqConfigEntity
import com.decibell.data.model.StandardBandConfigs
import com.decibell.data.repository.EqPreferencesRepository
import com.decibell.ui.theme.DecibellColors
import com.decibell.ui.theme.DecibellDimens
import com.decibell.ui.theme.DecibellShapes
import com.decibell.util.AppConstants

@Composable
fun EqFrequencyConfigDialog(
    initialBandCount: Int,
    initialIsDualChannel: Boolean,
    initialLeftCutoffs: List<Int>,
    initialRightCutoffs: List<Int>,
    activeConfigId: Long,
    activeConfigName: String,
    customConfigs: List<EqConfigEntity>,
    onInfoClick: () -> Unit,
    onSaveConfig: (
        existingId: Long,
        name: String,
        bandCount: Int,
        isDual: Boolean,
        leftHz: List<Int>,
        rightHz: List<Int>
    ) -> Unit,
    onDeleteConfig: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedConfigId by remember { mutableLongStateOf(activeConfigId) }
    var configName by remember {
        mutableStateOf(
            activeConfigName.ifBlank { "Custom ${initialBandCount}-Band" }
        )
    }
    var bandCount by remember { mutableIntStateOf(initialBandCount) }
    var isDualChannel by remember { mutableStateOf(initialIsDualChannel) }
    var isModified by remember { mutableStateOf(false) }

    val leftCutoffInputs = remember {
        mutableStateListOf<String>().apply {
            addAll(initialLeftCutoffs.map { it.toString() })
        }
    }
    val rightCutoffInputs = remember {
        mutableStateListOf<String>().apply {
            addAll(initialRightCutoffs.map { it.toString() })
        }
    }

    var showConfigDropdown by remember { mutableStateOf(false) }
    var showBandsDropdown by remember { mutableStateOf(false) }
    var showChannelsDropdown by remember { mutableStateOf(false) }

    fun resetBandInputs(newBandCount: Int, leftList: List<Int>, rightList: List<Int>) {
        leftCutoffInputs.clear()
        leftCutoffInputs.addAll(leftList.take(newBandCount).map { it.toString() })
        rightCutoffInputs.clear()
        rightCutoffInputs.addAll(rightList.take(newBandCount).map { it.toString() })
    }

    val parentContext = LocalContext.current
    val parentConfiguration = LocalConfiguration.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(
            LocalContext provides parentContext,
            LocalConfiguration provides parentConfiguration
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DecibellDimens.ScreenHorizontalPadding),
                shape = DecibellShapes.large,
                color = DecibellColors.SurfaceModal,
                border = BorderStroke(DecibellDimens.BorderThin, DecibellColors.BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
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
                        .padding(DecibellDimens.SpacingMd)
                ) {
                    // Header: Title + Info Icon on Left, Circular Close X Button on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.title_eq_config_hz),
                                style = MaterialTheme.typography.headlineSmall,
                                color = DecibellColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(DecibellDimens.SpacingXxs))
                            InfoIconButton(
                                onClick = onInfoClick,
                                testTagName = "eq_config_info_button"
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(DecibellDimens.MinTouchTarget)
                                .clip(CircleShape)
                                .background(DecibellColors.SurfaceInteractive)
                                .border(
                                    DecibellDimens.BorderThin,
                                    DecibellColors.BorderSubtle,
                                    CircleShape
                                )
                                .testTag("eq_config_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = DecibellColors.TextSecondary,
                                modifier = Modifier.size(DecibellDimens.IconSizeSmall)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))

                    // Top Configuration Metadata Controls (Directly on dialog surface, no inner card)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                    ) {
                        // Config Name Input + Saved Custom Configs Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.label_config),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DecibellColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                            Box(modifier = Modifier.weight(1f)) {
                                BasicTextField(
                                    value = configName,
                                    onValueChange = {
                                        configName = it
                                        isModified = true
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = DecibellColors.TextPrimary
                                    ),
                                    cursorBrush = SolidColor(DecibellColors.AccentBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DecibellShapes.extraSmall)
                                        .background(DecibellColors.SurfaceInteractive)
                                        .border(
                                            DecibellDimens.BorderThin,
                                            DecibellColors.BorderSubtle,
                                            DecibellShapes.extraSmall
                                        )
                                        .padding(
                                            horizontal = DecibellDimens.SpacingSm,
                                            vertical = DecibellDimens.SpacingXs
                                        )
                                        .testTag("eq_config_name_input")
                                )
                                DropdownMenu(
                                    expanded = showConfigDropdown,
                                    onDismissRequest = { showConfigDropdown = false },
                                    containerColor = DecibellColors.SurfaceInteractive
                                ) {
                                    CompositionLocalProvider(
                                        LocalContext provides parentContext,
                                        LocalConfiguration provides parentConfiguration
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.config_new_custom),
                                                    color = DecibellColors.TextPrimary
                                                )
                                            },
                                            onClick = {
                                                selectedConfigId = AppConstants.DEFAULT_CONFIG_ID
                                                configName = "Custom $bandCount-Band"
                                                val def = StandardBandConfigs.defaultCutoffsForCount(bandCount)
                                                resetBandInputs(bandCount, def, def)
                                                isModified = true
                                                showConfigDropdown = false
                                            }
                                        )
                                        customConfigs.forEach { cfg ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = cfg.name,
                                                        color = DecibellColors.TextPrimary
                                                    )
                                                },
                                                onClick = {
                                                    selectedConfigId = cfg.id
                                                    configName = cfg.name
                                                    bandCount = cfg.bandCount
                                                    isDualChannel = cfg.isDualChannel
                                                    val left = EqPreferencesRepository.parseCutoffsCsv(
                                                        cfg.leftCutoffsCsv,
                                                        cfg.bandCount
                                                    )
                                                    val right = EqPreferencesRepository.parseCutoffsCsv(
                                                        cfg.rightCutoffsCsv,
                                                        cfg.bandCount
                                                    )
                                                    resetBandInputs(cfg.bandCount, left, right)
                                                    isModified = false
                                                    showConfigDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (customConfigs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                                DecibellActionButton(
                                    text = stringResource(R.string.label_preset).removeSuffix(":"),
                                    onClick = { showConfigDropdown = true }
                                )
                            }
                        }

                        // Side-by-side Pill Selectors: Band Count & Channel Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingXs)
                        ) {
                            // Band Count Pill
                            Box(modifier = Modifier.weight(1f)) {
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
                                        .clickable { showBandsDropdown = true }
                                        .padding(
                                            horizontal = DecibellDimens.SpacingSm,
                                            vertical = DecibellDimens.SpacingXs
                                        )
                                        .testTag("eq_config_band_count_selector"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.label_nr_of_bands),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = DecibellColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = bandCount.toString(),
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

                                DropdownMenu(
                                    expanded = showBandsDropdown,
                                    onDismissRequest = { showBandsDropdown = false },
                                    containerColor = DecibellColors.SurfaceInteractive
                                ) {
                                    AppConstants.ALL_CONFIG_BAND_COUNTS.forEach { countOption ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = countOption.toString(),
                                                    color = DecibellColors.TextPrimary
                                                )
                                            },
                                            onClick = {
                                                bandCount = countOption
                                                val defaults = StandardBandConfigs.defaultCutoffsForCount(countOption)
                                                resetBandInputs(countOption, defaults, defaults)
                                                isModified = true
                                                showBandsDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Channel Mode Pill
                            Box(modifier = Modifier.weight(1f)) {
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
                                        .clickable { showChannelsDropdown = true }
                                        .padding(
                                            horizontal = DecibellDimens.SpacingSm,
                                            vertical = DecibellDimens.SpacingXs
                                        )
                                        .testTag("eq_config_channels_selector"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.label_channels),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = DecibellColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isDualChannel) {
                                                stringResource(R.string.channel_dual)
                                            } else {
                                                stringResource(R.string.channel_single)
                                            },
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

                                DropdownMenu(
                                    expanded = showChannelsDropdown,
                                    onDismissRequest = { showChannelsDropdown = false },
                                    containerColor = DecibellColors.SurfaceInteractive
                                ) {
                                    CompositionLocalProvider(
                                        LocalContext provides parentContext,
                                        LocalConfiguration provides parentConfiguration
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.channel_single),
                                                    color = DecibellColors.TextPrimary
                                                )
                                            },
                                            onClick = {
                                                isDualChannel = false
                                                isModified = true
                                                showChannelsDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.channel_dual),
                                                    color = DecibellColors.TextPrimary
                                                )
                                            },
                                            onClick = {
                                                isDualChannel = true
                                                isModified = true
                                                showChannelsDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Concise Formula Info Block
                        Text(
                            text = stringResource(R.string.desc_cutoff_frequency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DecibellColors.TextSecondary
                        )
                        Text(
                            text = stringResource(R.string.desc_cutoff_formula),
                            style = MaterialTheme.typography.labelSmall,
                            color = DecibellColors.AccentBlue,
                            modifier = Modifier.testTag("eq_config_formula_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))

                    // Column Header Row: Band # | Slider Center & Span | Upper Cut-off Hz
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = DecibellDimens.SpacingXs,
                                vertical = DecibellDimens.SpacingXxs
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.col_nr),
                            style = MaterialTheme.typography.labelMedium,
                            color = DecibellColors.TextSecondary,
                            modifier = Modifier.width(DecibellDimens.BandBadgeWidth)
                        )
                        Text(
                            text = stringResource(R.string.col_center_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = DecibellColors.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isDualChannel) {
                            Text(
                                text = stringResource(R.string.scale_left_short),
                                style = MaterialTheme.typography.labelMedium,
                                color = DecibellColors.TextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(DecibellDimens.ConfigInputWidth)
                            )
                            Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                            Text(
                                text = stringResource(R.string.scale_right_short),
                                style = MaterialTheme.typography.labelMedium,
                                color = DecibellColors.TextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(DecibellDimens.ConfigInputWidth)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.col_hz),
                                style = MaterialTheme.typography.labelMedium,
                                color = DecibellColors.TextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(DecibellDimens.ConfigInputWidth)
                            )
                        }
                    }

                    // Single Grouped Container for Bands 1..N (Separated by thin dividers, no individual row cards)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = DecibellDimens.ConfigDialogMaxHeight * 0.48f)
                            .clip(DecibellShapes.small)
                            .background(DecibellColors.BackgroundPrimary)
                            .border(
                                DecibellDimens.BorderThin,
                                DecibellColors.BorderSubtle,
                                DecibellShapes.small
                            )
                    ) {
                        itemsIndexed(leftCutoffInputs) { index, leftValue ->
                            val rightValue = rightCutoffInputs.getOrElse(index) { leftValue }
                            val prevLeftHz = if (index == 0) {
                                0
                            } else {
                                leftCutoffInputs.getOrNull(index - 1)?.toIntOrNull() ?: 0
                            }
                            val currLeftHz = leftValue.toIntOrNull() ?: 0
                            val centerLeftHz = StandardBandConfigs.computeBandCenterHz(prevLeftHz, currLeftHz)

                            val prevRightHz = if (index == 0) {
                                0
                            } else {
                                rightCutoffInputs.getOrNull(index - 1)?.toIntOrNull() ?: prevLeftHz
                            }
                            val currRightHz = rightValue.toIntOrNull() ?: currLeftHz
                            val centerRightHz = StandardBandConfigs.computeBandCenterHz(prevRightHz, currRightHz)

                            val previewText = if (isDualChannel) {
                                stringResource(
                                    R.string.format_center_dual,
                                    centerLeftHz,
                                    centerRightHz
                                )
                            } else {
                                stringResource(
                                    R.string.format_center_single,
                                    centerLeftHz,
                                    prevLeftHz,
                                    currLeftHz
                                )
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = DecibellDimens.SpacingSm,
                                            vertical = DecibellDimens.SpacingXs
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Band Number Label (#1, #2, ...)
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DecibellColors.AccentBlue,
                                        modifier = Modifier.width(DecibellDimens.BandBadgeWidth)
                                    )

                                    // Resulting Slider Center Hz & Span Range
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (currLeftHz > prevLeftHz) {
                                            DecibellColors.TextPrimary
                                        } else {
                                            DecibellColors.TextSecondary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))

                                    // Cut-off Frequency Input Box(es)
                                    CutoffNumberBox(
                                        value = leftValue,
                                        onValueChange = { newText ->
                                            val digits = newText.filter { it.isDigit() }.take(5)
                                            leftCutoffInputs[index] = digits
                                            if (!isDualChannel && index < rightCutoffInputs.size) {
                                                rightCutoffInputs[index] = digits
                                            }
                                            isModified = true
                                        },
                                        modifier = Modifier.width(DecibellDimens.ConfigInputWidth),
                                        testTagName = "cutoff_left_input_$index"
                                    )

                                    if (isDualChannel) {
                                        Spacer(modifier = Modifier.width(DecibellDimens.SpacingXs))
                                        CutoffNumberBox(
                                            value = rightValue,
                                            onValueChange = { newText ->
                                                val digits = newText.filter { it.isDigit() }.take(5)
                                                if (index < rightCutoffInputs.size) {
                                                    rightCutoffInputs[index] = digits
                                                }
                                                isModified = true
                                            },
                                            modifier = Modifier.width(DecibellDimens.ConfigInputWidth),
                                            testTagName = "cutoff_right_input_$index"
                                        )
                                    }
                                }

                                if (index < leftCutoffInputs.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = DecibellDimens.SpacingSm),
                                        thickness = DecibellDimens.DividerThickness,
                                        color = DecibellColors.DividerLine
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DecibellDimens.SpacingMd))

                    // Bottom Action Buttons: Secondary DELETE on Left | Primary SAVE on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingSm)
                    ) {
                        DecibellActionButton(
                            text = stringResource(R.string.action_delete),
                            onClick = {
                                if (selectedConfigId > 0L) {
                                    onDeleteConfig(selectedConfigId)
                                }
                            },
                            enabled = selectedConfigId > 0L,
                            isPrimary = false,
                            modifier = Modifier.weight(1f),
                            testTagName = "eq_config_delete_button"
                        )

                        DecibellActionButton(
                            text = if (isModified) {
                                stringResource(R.string.action_save_modified)
                            } else {
                                stringResource(R.string.action_save)
                            },
                            onClick = {
                                val parsedLeft = leftCutoffInputs.mapNotNull { it.toIntOrNull() }
                                val parsedRight = if (isDualChannel) {
                                    rightCutoffInputs.mapNotNull { it.toIntOrNull() }
                                } else {
                                    parsedLeft
                                }
                                onSaveConfig(
                                    selectedConfigId,
                                    configName,
                                    bandCount,
                                    isDualChannel,
                                    parsedLeft,
                                    parsedRight
                                )
                            },
                            isPrimary = true,
                            modifier = Modifier.weight(1f),
                            testTagName = "eq_config_save_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CutoffNumberBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = ""
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = DecibellColors.TextPrimary,
            textAlign = TextAlign.End
        ),
        cursorBrush = SolidColor(DecibellColors.AccentBlue),
        modifier = modifier
            .background(DecibellColors.BackgroundPrimary, DecibellShapes.extraSmall)
            .border(
                DecibellDimens.BorderThin,
                DecibellColors.BorderFocused,
                DecibellShapes.extraSmall
            )
            .padding(
                horizontal = DecibellDimens.SpacingSm,
                vertical = DecibellDimens.SpacingXs
            )
            .let { if (testTagName.isNotEmpty()) it.testTag(testTagName) else it }
    )
}
