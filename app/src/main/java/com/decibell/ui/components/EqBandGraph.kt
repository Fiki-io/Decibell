package com.decibell.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.decibell.R
import com.decibell.data.model.SliderSize
import com.decibell.data.model.StandardBandConfigs
import com.decibell.ui.theme.DecibellColors
import com.decibell.ui.theme.DecibellDimens
import com.decibell.ui.theme.DecibellShapes
import com.decibell.util.AppConstants
import com.decibell.util.LocaleHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqBandGraph(
    gainsDb: List<Float>,
    cutoffsHz: List<Int>,
    maxGainRangeDb: Float,
    sliderSize: SliderSize,
    visualizerEnabled: Boolean,
    spectrumBins: FloatArray,
    onBandGainChanged: (Int, Float) -> Unit,
    onScaleClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDragActiveChanged: (Boolean) -> Unit = {},
    channelTitle: String? = null,
    graphTestTag: String = "eq_band_graph"
) {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val bandCount = gainsDb.size.coerceAtLeast(1)
    val safeMaxDb = maxGainRangeDb.coerceIn(
        AppConstants.MIN_CUSTOM_MAX_GAIN_DB,
        AppConstants.MAX_CUSTOM_MAX_GAIN_DB
    )
    val maxDbInt = safeMaxDb.roundToInt()

    val displayFreqs = remember(cutoffsHz) {
        StandardBandConfigs.displayFrequenciesForCutoffs(cutoffsHz)
    }

    val graphHeight: Dp = when (sliderSize) {
        SliderSize.SMALL -> DecibellDimens.EqGraphHeightSmall
        SliderSize.MEDIUM -> DecibellDimens.EqGraphHeightMedium
        SliderSize.LARGE -> DecibellDimens.EqGraphHeightLarge
    }

    var activeDragBand by remember { mutableIntStateOf(-1) }
    val displayGains = remember(bandCount) {
        mutableStateListOf<Float>().apply {
            for (idx in 0 until bandCount) {
                add(gainsDb.getOrElse(idx) { 0f }.coerceIn(-safeMaxDb, safeMaxDb))
            }
        }
    }

    LaunchedEffect(gainsDb, bandCount, safeMaxDb) {
        for (idx in 0 until bandCount) {
            if (idx != activeDragBand) {
                val target = gainsDb.getOrElse(idx) { 0f }.coerceIn(-safeMaxDb, safeMaxDb)
                if (idx < displayGains.size && abs(displayGains[idx] - target) > 0.001f) {
                    displayGains[idx] = target
                }
            }
        }
    }

    val currentOnBandGainChanged by rememberUpdatedState(onBandGainChanged)
    val currentOnDragActiveChanged by rememberUpdatedState(onDragActiveChanged)

    val topTrackPadDp = DecibellDimens.SpacingXl
    val bottomTrackPadDp = DecibellDimens.EqThumbActiveRadius + DecibellDimens.SpacingXxs
    val topTrackPadPx = with(density) { topTrackPadDp.toPx() }
    val bottomTrackPadPx = with(density) { bottomTrackPadDp.toPx() }
    val labelRowHeight = DecibellDimens.SpacingXl + DecibellDimens.SpacingSm

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DecibellDimens.ScreenHorizontalPadding)
            .clip(DecibellShapes.medium)
            .background(DecibellColors.SurfaceCard)
            .background(DecibellColors.SurfaceCardOverlay)
            .border(
                DecibellDimens.BorderThin,
                DecibellColors.BorderSubtle,
                DecibellShapes.medium
            )
            .padding(
                top = DecibellDimens.SpacingSm,
                bottom = DecibellDimens.SpacingXs
            )
            .testTag(graphTestTag)
    ) {
        if (!channelTitle.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DecibellDimens.SpacingMd,
                        vertical = DecibellDimens.SpacingXxs
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(DecibellShapes.extraSmall)
                        .background(DecibellColors.SurfaceInteractive)
                        .border(
                            DecibellDimens.BorderThin,
                            DecibellColors.BorderSubtle,
                            DecibellShapes.extraSmall
                        )
                        .padding(
                            horizontal = DecibellDimens.SpacingSm,
                            vertical = DecibellDimens.SpacingXxs
                        )
                ) {
                    Text(
                        text = channelTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = DecibellColors.AccentBlue
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(graphHeight)
                .padding(horizontal = DecibellDimens.SpacingXs)
        ) {
            // Left dB Scale Column (+Max dB, 0, -Max) - Clickable to adjust dB range up to 40 dB
            Column(
                modifier = Modifier
                    .width(DecibellDimens.EqScaleColumnWidth)
                    .fillMaxHeight()
                    .padding(
                        top = DecibellDimens.SpacingXs,
                        bottom = labelRowHeight
                    )
                    .clip(DecibellShapes.extraSmall)
                    .clickable(onClick = onScaleClick)
                    .testTag("eq_db_scale_button"),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(DecibellShapes.extraSmall)
                        .background(DecibellColors.SurfaceInteractive)
                        .border(
                            DecibellDimens.BorderThin,
                            DecibellColors.BorderSubtle,
                            DecibellShapes.extraSmall
                        )
                        .padding(
                            horizontal = DecibellDimens.SpacingXxs,
                            vertical = DecibellDimens.SpacingXxs
                        )
                ) {
                    Text(
                        text = stringResource(R.string.scale_plus_format, maxDbInt),
                        style = MaterialTheme.typography.labelSmall,
                        color = DecibellColors.AccentBlue
                    )
                    Text(
                        text = stringResource(R.string.unit_db),
                        style = MaterialTheme.typography.labelSmall,
                        color = DecibellColors.TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(DecibellShapes.extraSmall)
                        .background(DecibellColors.BackgroundPrimary)
                        .padding(
                            horizontal = DecibellDimens.SpacingXxs,
                            vertical = DecibellDimens.SpacingXxs
                        )
                ) {
                    Text(
                        text = stringResource(R.string.scale_zero),
                        style = MaterialTheme.typography.labelSmall,
                        color = DecibellColors.TextSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.scale_minus_format, maxDbInt),
                    style = MaterialTheme.typography.labelSmall,
                    color = DecibellColors.AccentBlue,
                    modifier = Modifier
                        .clip(DecibellShapes.extraSmall)
                        .background(DecibellColors.SurfaceInteractive)
                        .border(
                            DecibellDimens.BorderThin,
                            DecibellColors.BorderSubtle,
                            DecibellShapes.extraSmall
                        )
                        .padding(
                            horizontal = DecibellDimens.SpacingXxs,
                            vertical = DecibellDimens.SpacingXxs
                        )
                )
            }

            // Scrollable / Adaptive Multi-Band Fader Area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val availableWidth = maxWidth
                val minRequiredWidth = DecibellDimens.EqBandMinColumnWidth * bandCount
                val useHorizontalScroll = minRequiredWidth > availableWidth
                val totalCanvasWidth = if (useHorizontalScroll) minRequiredWidth else availableWidth
                val bandColumnWidth = totalCanvasWidth / bandCount
                val scrollState = rememberScrollState()

                val scrollModifier = if (useHorizontalScroll) {
                    Modifier.horizontalScroll(scrollState, enabled = activeDragBand == -1)
                } else {
                    Modifier
                }

                Box(
                    modifier = scrollModifier
                        .width(totalCanvasWidth)
                        .fillMaxHeight()
                ) {
                    // Background Canvas: Studio Grid + Visualizer FFT + Smooth Cubic Response Curve + Capsule Faders
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = labelRowHeight)
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val topPad = topTrackPadPx
                        val bottomPad = bottomTrackPadPx
                        val usableHeight = (canvasHeight - topPad - bottomPad).coerceAtLeast(1f)
                        val colWidthPx = canvasWidth / bandCount
                        val zeroY = topPad + usableHeight * 0.5f
                        val upperQuarterY = topPad + usableHeight * 0.25f
                        val lowerQuarterY = topPad + usableHeight * 0.75f

                        // 1. Draw FFT Spectrum Visualizer if active
                        if (visualizerEnabled && spectrumBins.isNotEmpty()) {
                            val binWidth = canvasWidth / spectrumBins.size
                            for (b in spectrumBins.indices) {
                                val norm = spectrumBins[b].coerceIn(0f, 1f)
                                if (norm > 0.01f) {
                                    val barHeight = norm * usableHeight
                                    drawRoundRect(
                                        color = DecibellColors.VisualizerSpectrum,
                                        topLeft = Offset(
                                            x = b * binWidth + 2f,
                                            y = topPad + usableHeight - barHeight
                                        ),
                                        size = Size(
                                            width = (binWidth - 4f).coerceAtLeast(2f),
                                            height = barHeight
                                        ),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                }
                            }
                        }

                        // 2. Draw horizontal studio reference lines (+50%, 0 dB, -50%)
                        drawLine(
                            color = DecibellColors.DividerLine,
                            start = Offset(0f, upperQuarterY),
                            end = Offset(canvasWidth, upperQuarterY),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = DecibellColors.ZeroLine,
                            start = Offset(0f, zeroY),
                            end = Offset(canvasWidth, zeroY),
                            strokeWidth = 1.5f
                        )
                        drawLine(
                            color = DecibellColors.DividerLine,
                            start = Offset(0f, lowerQuarterY),
                            end = Offset(canvasWidth, lowerQuarterY),
                            strokeWidth = 1f
                        )

                        // Compute thumb centers from persistent displayGains (never switches source on release)
                        val points = List(bandCount) { idx ->
                            val rawGain = if (idx < displayGains.size) {
                                displayGains[idx]
                            } else {
                                gainsDb.getOrElse(idx) { 0f }
                            }
                            val gain = rawGain.coerceIn(-safeMaxDb, safeMaxDb)
                            val normalized = (safeMaxDb - gain) / (safeMaxDb * 2f)
                            val cx = colWidthPx * idx + colWidthPx * 0.5f
                            val cy = topPad + normalized * usableHeight
                            Offset(cx, cy)
                        }

                        // 3. Draw smooth cubic Bezier response curve & area fill
                        if (points.size >= 2) {
                            val strokePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 0 until points.size - 1) {
                                    val p0 = points[i]
                                    val p1 = points[i + 1]
                                    val controlX = (p0.x + p1.x) * 0.5f
                                    cubicTo(
                                        controlX, p0.y,
                                        controlX, p1.y,
                                        p1.x, p1.y
                                    )
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(strokePath)
                                lineTo(points.last().x, topPad + usableHeight)
                                lineTo(points.first().x, topPad + usableHeight)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        DecibellColors.AccentBlueFillTop,
                                        DecibellColors.AccentBlueFillBottom
                                    ),
                                    startY = topPad,
                                    endY = topPad + usableHeight
                                )
                            )

                            drawPath(
                                path = strokePath,
                                color = DecibellColors.AccentBlue,
                                style = Stroke(width = DecibellDimens.EqCurveStrokeWidth.toPx())
                            )
                        }

                        // 4. Draw individual vertical tracks and hardware capsule fader caps
                        val trackStroke = DecibellDimens.EqTrackStrokeWidth.toPx()
                        val capW = DecibellDimens.EqThumbCapsuleWidth.toPx()
                        val capH = DecibellDimens.EqThumbCapsuleHeight.toPx()
                        val activeCapW = DecibellDimens.EqThumbCapsuleActiveWidth.toPx()
                        val activeCapH = DecibellDimens.EqThumbCapsuleActiveHeight.toPx()
                        val capCorner = DecibellDimens.EqThumbCapsuleCorner.toPx()

                        for (idx in 0 until bandCount) {
                            val pt = points[idx]
                            val isDraggingThis = activeDragBand == idx
                            // Upper unlit track slot
                            drawLine(
                                color = DecibellColors.TrackInactive,
                                start = Offset(pt.x, topPad),
                                end = Offset(pt.x, pt.y),
                                strokeWidth = trackStroke,
                                cap = StrokeCap.Round
                            )
                            // Active illuminated track from bottom to thumb
                            drawLine(
                                color = DecibellColors.AccentBlue,
                                start = Offset(pt.x, pt.y),
                                end = Offset(pt.x, topPad + usableHeight),
                                strokeWidth = trackStroke,
                                cap = StrokeCap.Round
                            )

                            // Capsule fader cap (distinct from SpotEQ31's plain circle dot)
                            val w = if (isDraggingThis) activeCapW else capW
                            val h = if (isDraggingThis) activeCapH else capH
                            val topLeft = Offset(pt.x - w * 0.5f, pt.y - h * 0.5f)
                            val cornerRadius = CornerRadius(capCorner, capCorner)

                            drawRoundRect(
                                color = if (isDraggingThis) {
                                    DecibellColors.AccentBlue
                                } else {
                                    DecibellColors.SurfaceInteractivePressed
                                },
                                topLeft = topLeft,
                                size = Size(w, h),
                                cornerRadius = cornerRadius
                            )
                            drawRoundRect(
                                color = if (isDraggingThis) {
                                    DecibellColors.TextPrimary
                                } else {
                                    DecibellColors.AccentBlue
                                },
                                topLeft = topLeft,
                                size = Size(w, h),
                                cornerRadius = cornerRadius,
                                style = Stroke(width = 1.5f)
                            )
                            // Center horizontal grip line inside fader cap
                            val gripHalfWidth = w * 0.28f
                            drawLine(
                                color = DecibellColors.TextPrimary,
                                start = Offset(pt.x - gripHalfWidth, pt.y),
                                end = Offset(pt.x + gripHalfWidth, pt.y),
                                strokeWidth = 1.5f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Interactive touch columns + real-time dB label at top + frequency labels at bottom
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (bandIndex in 0 until bandCount) {
                            val currentGain = if (bandIndex < displayGains.size) {
                                displayGains[bandIndex]
                            } else {
                                gainsDb.getOrElse(bandIndex) { 0f }
                            }
                            val freqHz = displayFreqs.getOrElse(bandIndex) { 1000 }
                            val freqLabel = LocaleHelper.formatFrequencyLabel(freqHz)

                            Column(
                                modifier = Modifier
                                    .width(bandColumnWidth)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Unified Interactive Fader Zone:
                                // Handles press, continuous vertical drag, 0 dB detent haptic, and double-tap reset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .testTag("eq_band_slider_$bandIndex")
                                        .pointerInput(bandIndex, bandCount, safeMaxDb, useHorizontalScroll) {
                                            var lastTapUpTimeMs = 0L
                                            var lastTapPosition = Offset.Zero

                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                if (!useHorizontalScroll) {
                                                    down.consume()
                                                }
                                                val downTimeMs = System.currentTimeMillis()
                                                val isDoubleTapCandidate =
                                                    (downTimeMs - lastTapUpTimeMs) in 40L..280L &&
                                                        (down.position - lastTapPosition).getDistance() <
                                                        viewConfiguration.touchSlop * 2f

                                                val initialPrevGain = if (bandIndex < displayGains.size) {
                                                    displayGains[bandIndex]
                                                } else {
                                                    0f
                                                }
                                                var wasAtZero = abs(initialPrevGain) < 0.01f

                                                currentOnDragActiveChanged(true)
                                                activeDragBand = bandIndex

                                                try {
                                                    val initialGain = yOffsetToGainDbWithDetent(
                                                        y = down.position.y,
                                                        heightPx = size.height.toFloat(),
                                                        topPadPx = topTrackPadPx,
                                                        bottomPadPx = bottomTrackPadPx,
                                                        maxDb = safeMaxDb
                                                    )
                                                    if (bandIndex < displayGains.size) {
                                                        displayGains[bandIndex] = initialGain
                                                    }

                                                    val initialNowAtZero = abs(initialGain) < 0.01f
                                                    if (initialNowAtZero && !wasAtZero) {
                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                    wasAtZero = initialNowAtZero
                                                    currentOnBandGainChanged(bandIndex, initialGain)

                                                    var draggedVertically = false
                                                    var cancelledByHorizontalScroll = false

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                            ?: event.changes.firstOrNull()
                                                        if (change == null || !change.pressed) {
                                                            break
                                                        }

                                                        val dx = change.position.x - down.position.x
                                                        val dy = change.position.y - down.position.y

                                                        if (useHorizontalScroll &&
                                                            !draggedVertically &&
                                                            abs(dx) > viewConfiguration.touchSlop &&
                                                            abs(dx) > abs(dy) * 1.4f
                                                        ) {
                                                            cancelledByHorizontalScroll = true
                                                            break
                                                        }

                                                        if (abs(dy) > viewConfiguration.touchSlop * 0.3f) {
                                                            draggedVertically = true
                                                        }

                                                        change.consume()
                                                        val newGain = yOffsetToGainDbWithDetent(
                                                            y = change.position.y,
                                                            heightPx = size.height.toFloat(),
                                                            topPadPx = topTrackPadPx,
                                                            bottomPadPx = bottomTrackPadPx,
                                                            maxDb = safeMaxDb
                                                        )
                                                        activeDragBand = bandIndex
                                                        if (bandIndex < displayGains.size) {
                                                            displayGains[bandIndex] = newGain
                                                        }

                                                        val nowAtZero = abs(newGain) < 0.01f
                                                        if (nowAtZero && !wasAtZero) {
                                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                        wasAtZero = nowAtZero
                                                        currentOnBandGainChanged(bandIndex, newGain)
                                                    }

                                                    if (!cancelledByHorizontalScroll && !draggedVertically) {
                                                        val upTimeMs = System.currentTimeMillis()
                                                        if (isDoubleTapCandidate) {
                                                            lastTapUpTimeMs = 0L
                                                            if (bandIndex < displayGains.size) {
                                                                displayGains[bandIndex] = 0.0f
                                                            }
                                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            currentOnBandGainChanged(bandIndex, 0.0f)
                                                        } else {
                                                            lastTapUpTimeMs = upTimeMs
                                                            lastTapPosition = down.position
                                                        }
                                                    } else {
                                                        lastTapUpTimeMs = 0L
                                                    }
                                                } finally {
                                                    if (activeDragBand == bandIndex) {
                                                        activeDragBand = -1
                                                    }
                                                    currentOnDragActiveChanged(false)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    if (activeDragBand == bandIndex) {
                                        val formattedDb = if (abs(currentGain) < 0.05f) {
                                            "0.0"
                                        } else {
                                            String.format(Locale.US, "%+.1f", currentGain)
                                        }
                                        Text(
                                            text = formattedDb,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DecibellColors.AccentBlue,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .clip(DecibellShapes.extraSmall)
                                                .background(DecibellColors.BackgroundPrimary)
                                                .padding(horizontal = DecibellDimens.SpacingXxs)
                                                .testTag("eq_band_active_db_$bandIndex")
                                        )
                                    }
                                }

                                // Frequency label at bottom
                                Column(
                                    modifier = Modifier.height(labelRowHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Text(
                                        text = freqLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeDragBand == bandIndex) {
                                            DecibellColors.AccentBlue
                                        } else {
                                            DecibellColors.TextPrimary
                                        },
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    if (bandIndex == 0) {
                                        Text(
                                            text = stringResource(R.string.unit_hz),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DecibellColors.TextSecondary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(DecibellDimens.SpacingSm))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun yOffsetToGainDbWithDetent(
    y: Float,
    heightPx: Float,
    topPadPx: Float,
    bottomPadPx: Float,
    maxDb: Float
): Float {
    val usable = (heightPx - topPadPx - bottomPadPx).coerceAtLeast(1f)
    val clampedY = (y - topPadPx).coerceIn(0f, usable)
    val fractionFromTop = clampedY / usable
    val rawDb = maxDb - fractionFromTop * (maxDb * 2f)
    val detentThresholdDb = maxDb * AppConstants.ZERO_DETENT_FRACTION
    if (abs(rawDb) <= detentThresholdDb) {
        return 0.0f
    }
    return ((rawDb * 10f).roundToInt() / 10f).coerceIn(-maxDb, maxDb)
}
