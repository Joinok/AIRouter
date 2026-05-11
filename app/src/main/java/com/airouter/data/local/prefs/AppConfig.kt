package com.airouter.data.local.prefs

import com.tencent.mmkv.MMKV

class AppConfig {

    private val mmkv: MMKV = MMKV.defaultMMKV()

    companion object {
        private const val KEY_CURRENT_THEME = "current_theme"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_LAST_SESSION_ID = "last_session_id"
        private const val KEY_STREAM_ENABLED = "stream_enabled"
        private const val KEY_SHOW_TOKEN_USAGE = "show_token_usage"
        private const val KEY_DEBUG_LOG_ENABLED = "debug_log_enabled"
    }

    var currentTheme: String
        get() = mmkv.decodeString(KEY_CURRENT_THEME) ?: "system"
        set(value) { mmkv.encode(KEY_CURRENT_THEME, value) }

    var defaultProviderId: String
        get() = mmkv.decodeString(KEY_DEFAULT_PROVIDER) ?: "deepseek"
        set(value) { mmkv.encode(KEY_DEFAULT_PROVIDER, value) }

    var defaultModelId: String
        get() = mmkv.decodeString(KEY_DEFAULT_MODEL) ?: "deepseek-chat"
        set(value) { mmkv.encode(KEY_DEFAULT_MODEL, value) }

    var lastSessionId: String?
        get() = mmkv.decodeString(KEY_LAST_SESSION_ID)
        set(value) { mmkv.encode(KEY_LAST_SESSION_ID, value ?: "") }

    var streamEnabled: Boolean
        get() = mmkv.decodeBool(KEY_STREAM_ENABLED, true)
        set(value) { mmkv.encode(KEY_STREAM_ENABLED, value) }

    var showTokenUsage: Boolean
        get() = mmkv.decodeBool(KEY_SHOW_TOKEN_USAGE, true)
        set(value) { mmkv.encode(KEY_SHOW_TOKEN_USAGE, value) }

    var debugLogEnabled: Boolean
        get() = mmkv.decodeBool(KEY_DEBUG_LOG_ENABLED, false)
        set(value) { mmkv.encode(KEY_DEBUG_LOG_ENABLED, value) }
}
