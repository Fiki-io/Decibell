package com.decibell.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decibell.R
import com.decibell.ui.theme.DecibellColors
import com.decibell.ui.theme.DecibellDimens
import com.decibell.ui.theme.DecibellMotion
import com.decibell.ui.theme.DecibellShapes

@Composable
fun LayeredGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DecibellShapes.medium,
        color = DecibellColors.SurfaceCard,
        border = BorderStroke(DecibellDimens.BorderThin, DecibellColors.BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .background(DecibellColors.SurfaceCardOverlay)
                .padding(
                    horizontal = DecibellDimens.SpacingMd,
                    vertical = DecibellDimens.SpacingSm
                ),
            content = content
        )
    }
}

@Composable
fun DecibellActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    testTagName: String = ""
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animationsEnabled = remember(context) { DecibellMotion.isAnimationEnabled(context) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) {
            DecibellMotion.PRESS_SCALE_TARGET
        } else {
            DecibellMotion.PRESS_SCALE_DEFAULT
        },
        animationSpec = DecibellMotion.fastTween(animationsEnabled),
        label = "button_press_scale"
    )

    val containerColor = when {
        !enabled -> DecibellColors.SurfaceCard
        isPrimary -> DecibellColors.AccentBlue
        else -> DecibellColors.SurfaceInteractive
    }
    val borderColor = if (isPrimary) DecibellColors.BorderFocused else DecibellColors.BorderSubtle
    val textColor = if (enabled) DecibellColors.TextPrimary else DecibellColors.TextDisabled

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(DecibellShapes.small)
            .background(containerColor)
            .border(DecibellDimens.BorderThin, borderColor, DecibellShapes.small)
            .let { if (testTagName.isNotEmpty()) it.testTag(testTagName) else it }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(
                horizontal = DecibellDimens.SpacingMd,
                vertical = DecibellDimens.SpacingSm
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun InfoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = ""
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(DecibellDimens.MinTouchTarget)
            .let { if (testTagName.isNotEmpty()) it.testTag(testTagName) else it }
    ) {
        Box(
            modifier = Modifier
                .size(DecibellDimens.IconSizeStandard)
                .border(DecibellDimens.BorderThin, DecibellColors.TextSecondary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.cd_info),
                tint = DecibellColors.TextSecondary,
                modifier = Modifier.size(DecibellDimens.IconSizeSmall)
            )
        }
    }
}

@Composable
fun ParameterSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    snapToZeroDetent: Boolean = false,
    leftScaleLabel: String? = null,
    rightScaleLabel: String? = null,
    onInfoClick: (() -> Unit)? = null,
    sliderTestTag: String = ""
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val haptics = LocalHapticFeedback.current
    var wasAtZero by remember { androidx.compose.runtime.mutableStateOf(kotlin.math.abs(value) < 0.01f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DecibellDimens.SpacingXs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) DecibellColors.TextPrimary else DecibellColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onInfoClick != null) {
                Spacer(modifier = Modifier.width(DecibellDimens.SpacingXxs))
                InfoIconButton(onClick = onInfoClick)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = DecibellColors.TextPrimary,
                maxLines = 1
            )
        }

        if (leftScaleLabel != null || rightScaleLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DecibellDimens.SpacingXs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = leftScaleLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = DecibellColors.TextSecondary
                )
                Text(
                    text = rightScaleLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = DecibellColors.TextSecondary
                )
            }
        }

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { rawValue ->
                if (snapToZeroDetent && valueRange.start < 0f && valueRange.endInclusive > 0f) {
                    val span = valueRange.endInclusive - valueRange.start
                    val detentZone = span * com.decibell.util.AppConstants.ZERO_DETENT_FRACTION
                    val resolved = if (kotlin.math.abs(rawValue) <= detentZone) 0.0f else rawValue
                    val nowAtZero = kotlin.math.abs(resolved) < 0.01f
                    if (nowAtZero && !wasAtZero) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    wasAtZero = nowAtZero
                    onValueChange(resolved)
                } else {
                    onValueChange(rawValue)
                }
            },
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .let { if (sliderTestTag.isNotEmpty()) it.testTag(sliderTestTag) else it },
            colors = SliderDefaults.colors(
                thumbColor = DecibellColors.AccentBlue,
                activeTrackColor = DecibellColors.AccentBlue,
                inactiveTrackColor = DecibellColors.TrackInactive,
                disabledThumbColor = DecibellColors.TextDisabled,
                disabledActiveTrackColor = DecibellColors.TrackInactive,
                disabledInactiveTrackColor = DecibellColors.TrackInactive
            )
        )
    }
}

@Composable
fun DecibellModalDialog(
    title: String,
    description: String?,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bodyContent: (@Composable ColumnScope.() -> Unit)? = null
) {
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
                modifier = modifier
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
                    .padding(DecibellDimens.SpacingLg)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = DecibellColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = DecibellDimens.MinTouchTarget)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(DecibellDimens.MinTouchTarget)
                            .testTag("dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = DecibellColors.TextSecondary
                        )
                    }
                }

                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DecibellColors.TextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (bodyContent != null) {
                    Spacer(modifier = Modifier.height(DecibellDimens.SpacingMd))
                    bodyContent()
                }

                Spacer(modifier = Modifier.height(DecibellDimens.SpacingLg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DecibellDimens.SpacingSm)
                ) {
                    DecibellActionButton(
                        text = cancelText,
                        onClick = onDismiss,
                        isPrimary = false,
                        modifier = Modifier.weight(1f),
                        testTagName = "dialog_secondary_button"
                    )
                    DecibellActionButton(
                        text = confirmText,
                        onClick = onConfirm,
                        isPrimary = true,
                        modifier = Modifier.weight(1f),
                        testTagName = "dialog_primary_button"
                    )
                }
            }
        }
        }
    }
}

data class SheetOptionItem<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DecibellSelectionBottomSheet(
    title: String,
    options: List<SheetOptionItem<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    headerActionText: String? = null,
    onHeaderActionClick: (() -> Unit)? = null
) {
    val parentContext = LocalContext.current
    val parentConfiguration = LocalConfiguration.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .padding(
                    top = DecibellDimens.SpacingMd,
                    bottom = DecibellDimens.SpacingXl
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DecibellDimens.ScreenHorizontalPadding)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DecibellColors.TextPrimary,
                    modifier = Modifier.align(Alignment.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

            if (headerActionText != null && onHeaderActionClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = DecibellDimens.ScreenHorizontalPadding,
                            vertical = DecibellDimens.SpacingXs
                        ),
                    horizontalArrangement = Arrangement.End
                ) {
                    DecibellActionButton(
                        text = headerActionText,
                        onClick = {
                            onDismiss()
                            onHeaderActionClick()
                        },
                        isPrimary = false,
                        testTagName = "sheet_header_action_button"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(options) { index, item ->
                    val isSelected = item.value == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DecibellDimens.RowHeightStandard)
                            .clickable {
                                onSelect(item.value)
                                onDismiss()
                            }
                            .padding(horizontal = DecibellDimens.ScreenHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) {
                                    DecibellColors.TextPrimary
                                } else {
                                    DecibellColors.TextSecondary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!item.subtitle.isNullOrBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DecibellColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = DecibellColors.TextPrimary,
                                modifier = Modifier.size(DecibellDimens.IconSizeStandard)
                            )
                        }
                    }
                    if (index < options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = DecibellDimens.ScreenHorizontalPadding),
                            thickness = DecibellDimens.DividerThickness,
                            color = DecibellColors.DividerLine
                        )
                    }
                }
            }
        }
        }
    }
}
