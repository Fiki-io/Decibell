package com.decibell.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocaleHelper {
    private val initialSystemLocale: Locale =
        runCatching { Resources.getSystem().configuration.locales.get(0) }.getOrNull()
            ?: Locale.getDefault()

    fun resolveLocale(languageCode: String): Locale {
        if (languageCode == AppConstants.LANG_SYSTEM || languageCode.isBlank()) {
            return runCatching { Resources.getSystem().configuration.locales.get(0) }.getOrNull()
                ?: initialSystemLocale
        }
        return Locale.forLanguageTag(languageCode)
    }

    @Suppress("DEPRECATION")
    fun wrapContext(base: Context, languageCode: String): Context {
        val locale = resolveLocale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        base.resources.updateConfiguration(config, base.resources.displayMetrics)
        base.applicationContext?.resources?.let { appRes ->
            val appConfig = Configuration(appRes.configuration)
            appConfig.setLocale(locale)
            appConfig.setLayoutDirection(locale)
            appRes.updateConfiguration(appConfig, appRes.displayMetrics)
        }

        return base.createConfigurationContext(config)
    }

    fun formatFrequencyLabel(hz: Int): String {
        return when {
            hz >= 10000 -> "${hz / 1000}k"
            hz >= 1000 -> {
                val k = hz / 1000.0f
                val roundedTenths = Math.round(k * 10f) / 10f
                if (roundedTenths % 1.0f == 0.0f) {
                    "${roundedTenths.toInt()}k"
                } else {
                    String.format(Locale.US, "%.1fk", roundedTenths)
                }
            }
            else -> hz.toString()
        }
    }
}
