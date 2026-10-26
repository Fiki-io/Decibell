package com.decibell.util

object AppConstants {
    const val DATABASE_NAME = "decibell_eq.db"
    const val DATASTORE_NAME = "decibell_preferences"

    // DataStore Preference Keys
    const val KEY_EQ_ENABLED = "eq_enabled"
    const val KEY_BAND_COUNT = "band_count"
    const val KEY_CHANNEL_JOINED = "channel_joined"
    const val KEY_ACTIVE_PRESET_NAME = "active_preset_name"
    const val KEY_ACTIVE_CONFIG_ID = "active_config_id"
    const val KEY_LEFT_GAINS = "left_gains_csv"
    const val KEY_RIGHT_GAINS = "right_gains_csv"
    const val KEY_MAX_GAIN_RANGE_DB = "max_gain_range_db"
    const val KEY_SLIDER_SIZE = "slider_size"
    const val KEY_VISUALIZER_ENABLED = "visualizer_enabled"
    const val KEY_VISUALIZER_INTERVAL_MS = "visualizer_interval_ms"
    const val KEY_PRE_GAIN_DB = "pre_gain_db"
    const val KEY_BALANCE_DB = "balance_db"
    const val KEY_EFFECTS_EXPANDED = "effects_expanded"
    const val KEY_REVERB_PRESET = "reverb_preset"
    const val KEY_BASS_BOOST_STRENGTH = "bass_boost_strength"
    const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
    const val KEY_AUTO_PAN_RATE_HZ = "auto_pan_rate_hz"
    const val KEY_MBC_ENABLED = "mbc_enabled"
    const val KEY_MBC_PRESET = "mbc_preset"
    const val KEY_LIMITER_ENABLED = "limiter_enabled"
    const val KEY_LIMITER_ATTACK_MS = "limiter_attack_ms"
    const val KEY_LIMITER_RELEASE_MS = "limiter_release_ms"
    const val KEY_LIMITER_RATIO = "limiter_ratio"
    const val KEY_LIMITER_THRESHOLD_DB = "limiter_threshold_db"
    const val KEY_LIMITER_POST_GAIN_DB = "limiter_post_gain_db"
    const val KEY_GLOBAL_MIX_FALLBACK = "global_mix_fallback"
    const val KEY_APP_LANGUAGE = "app_language"

    // Supported Band Counts (matching SpotEQ31 main selector & EQ Config Hz editor)
    val MAIN_BAND_COUNTS = listOf(7, 11, 15, 21, 31)
    val ALL_CONFIG_BAND_COUNTS = listOf(7, 11, 15, 21, 27, 31, 37, 41)
    val QUICK_DB_RANGES = listOf(15, 20, 25, 30, 40)

    // Audio Parameter Limits
    const val DEFAULT_BAND_COUNT = 7
    const val DEFAULT_MAX_GAIN_DB = 15.0f
    const val MIN_CUSTOM_MAX_GAIN_DB = 5.0f
    const val MAX_CUSTOM_MAX_GAIN_DB = 40.0f
    const val SINGLE_STAGE_EQ_MAX_DB = 20.0f

    const val MIN_GAIN_DB = -40.0f
    const val MAX_GAIN_DB = 40.0f
    const val DEFAULT_GAIN_DB = 0.0f
    const val DEFAULT_BALANCE_RANGE_DB = 15.0f

    // Center 0 dB detent snap zone as a fraction of full slider range (2.4%)
    const val ZERO_DETENT_FRACTION = 0.024f

    const val MIN_CUTOFF_HZ = 20
    const val MAX_CUTOFF_HZ = 20000

    // DynamicsProcessing FFT Resolution Configuration (AOSP DPFrequency.cpp MAX_ENFORCE_BLOCK_SIZE = 4096)
    const val MAX_HARDWARE_EQ_BANDS = 41
    const val DP_PREFERRED_FRAME_DURATION_MS = 85.0f
    const val DP_FALLBACK_FRAME_DURATION_MS = 42.0f
    const val DP_MAX_FFT_BLOCK_SIZE = 4096
    const val DP_DEFAULT_OUTPUT_SAMPLE_RATE_HZ = 48000
    const val SUB_BASS_COUPLING_MAX_HZ = 160
    const val NARROW_BAND_COUPLING_FACTOR = 0.42f

    const val MIN_VISUALIZER_MS = 100
    const val MAX_VISUALIZER_MS = 1000
    const val DEFAULT_VISUALIZER_MS = 300
    const val VISUALIZER_BINS = 32

    const val MIN_EFFECT_STRENGTH = 0
    const val MAX_EFFECT_STRENGTH = 1000

    const val MIN_AUTO_PAN_HZ = 0.0f
    const val MAX_AUTO_PAN_HZ = 2.0f
    const val AUTO_PAN_DEPTH_DB = 6.0f
    const val AUTO_PAN_TICK_MS = 40L

    const val MIN_LIMITER_ATTACK_MS = 1.0f
    const val MAX_LIMITER_ATTACK_MS = 100.0f
    const val DEFAULT_LIMITER_ATTACK_MS = 1.0f

    const val MIN_LIMITER_RELEASE_MS = 1.0f
    const val MAX_LIMITER_RELEASE_MS = 500.0f
    const val DEFAULT_LIMITER_RELEASE_MS = 60.0f

    const val MIN_LIMITER_RATIO = 1.0f
    const val MAX_LIMITER_RATIO = 20.0f
    const val DEFAULT_LIMITER_RATIO = 4.0f

    const val MIN_LIMITER_THRESHOLD_DB = -30.0f
    const val MAX_LIMITER_THRESHOLD_DB = 0.0f
    const val DEFAULT_LIMITER_THRESHOLD_DB = -2.0f

    const val MIN_LIMITER_POST_GAIN_DB = -15.0f
    const val MAX_LIMITER_POST_GAIN_DB = 15.0f
    const val DEFAULT_LIMITER_POST_GAIN_DB = 0.0f

    const val GLOBAL_AUDIO_SESSION_ID = 0
    const val DEFAULT_PRESET_FLAT = "Flat"
    const val CUSTOM_PRESET_KEY = "Custom"
    const val DEFAULT_CONFIG_ID = -1L

    // Service & Intents
    const val NOTIFICATION_CHANNEL_ID = "decibell_eq_engine_channel"
    const val NOTIFICATION_ID = 3101
    const val ACTION_START_OR_UPDATE_ENGINE = "com.decibell.action.START_OR_UPDATE_ENGINE"
    const val ACTION_STOP_ENGINE = "com.decibell.action.STOP_ENGINE"
    const val ACTION_SESSION_OPENED = "com.decibell.action.SESSION_OPENED"
    const val ACTION_SESSION_CLOSED = "com.decibell.action.SESSION_CLOSED"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

    // Backup JSON
    const val MIME_TYPE_JSON = "application/json"
    const val DEFAULT_EXPORT_FILENAME = "decibell_backup.json"

    // Supported Language Codes
    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_ID = "id"
    const val LANG_ES = "es"
    const val LANG_JA = "ja"
}
