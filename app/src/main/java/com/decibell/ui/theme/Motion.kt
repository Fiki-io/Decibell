package com.decibell.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object DecibellMotion {
    const val DURATION_INSTANT = 0
    const val DURATION_FAST = 130
    const val DURATION_STANDARD = 220
    const val DURATION_LONG = 320

    const val PRESS_SCALE_TARGET = 0.97f
    const val PRESS_SCALE_DEFAULT = 1.0f
    const val DIALOG_INITIAL_SCALE = 0.92f

    private const val SPRING_DAMPING_RATIO = 0.82f

    val StandardEasing: Easing = FastOutSlowInEasing

    fun isAnimationEnabled(context: Context): Boolean {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (_: Exception) {
            1.0f
        }
        return scale > 0.0f
    }

    fun resolveDuration(baseDuration: Int, animationsEnabled: Boolean): Int {
        return if (animationsEnabled) baseDuration else DURATION_INSTANT
    }

    fun <T> fastTween(animationsEnabled: Boolean = true): TweenSpec<T> =
        tween(
            durationMillis = resolveDuration(DURATION_FAST, animationsEnabled),
            easing = StandardEasing
        )

    fun <T> standardTween(animationsEnabled: Boolean = true): TweenSpec<T> =
        tween(
            durationMillis = resolveDuration(DURATION_STANDARD, animationsEnabled),
            easing = StandardEasing
        )

    fun <T> longTween(animationsEnabled: Boolean = true): TweenSpec<T> =
        tween(
            durationMillis = resolveDuration(DURATION_LONG, animationsEnabled),
            easing = StandardEasing
        )

    fun <T> standardSpring(): SpringSpec<T> =
        spring(
            dampingRatio = SPRING_DAMPING_RATIO,
            stiffness = Spring.StiffnessMedium
        )
}
