package com.memoria.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object UserPrefs {

    private const val PREFS_NAME = "memoria_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LOST_MODE = "lost_mode"
    private const val KEY_SHARING_ACTIVE = "sharing_active"
    private const val KEY_LAST_LAT = "last_lat"
    private const val KEY_LAST_LNG = "last_lng"
    private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
    private const val KEY_TG_CHAT_ID = "tg_chat_id"
    private const val KEY_TAP_PATTERN = "tap_pattern"
    private const val KEY_TAP_LEVEL = "tap_level"
    private const val KEY_SHAKE_PATTERN = "shake_pattern"
    private const val KEY_SHAKE_LEVEL = "shake_level"
    private const val KEY_SMTP_HOST = "smtp_host"
    private const val KEY_SMTP_PORT = "smtp_port"
    private const val KEY_SMTP_USER = "smtp_user"
    private const val KEY_SMTP_PASS = "smtp_pass"
    private const val KEY_SMTP_FROM = "smtp_from"
    private const val KEY_EMAIL_TO = "email_to"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTelegramBotToken(context: Context): String? =
        prefs(context).getString(KEY_TG_BOT_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setTelegramBotToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TG_BOT_TOKEN, token.trim()).apply()
    }

    fun getTelegramChatId(context: Context): String? =
        getTelegramChatIds(context).firstOrNull()

    fun getTelegramChatIds(context: Context): List<String> =
        prefs(context).getString(KEY_TG_CHAT_ID, null)
            ?.split(',', '\n', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    fun setTelegramChatIds(context: Context, chatIds: List<String>) {
        prefs(context).edit().putString(KEY_TG_CHAT_ID, chatIds.joinToString(",")).apply()
    }

    fun getTapPattern(context: Context): PatternConfig =
        PatternConfig(
            count = prefs(context).getInt(KEY_TAP_PATTERN, 3),
            level = prefs(context).getInt(KEY_TAP_LEVEL, LEVEL_1ST)
        )

    fun setTapPattern(context: Context, config: PatternConfig) {
        prefs(context).edit()
            .putInt(KEY_TAP_PATTERN, config.count)
            .putInt(KEY_TAP_LEVEL, config.level)
            .apply()
    }

    fun getShakePattern(context: Context): PatternConfig =
        PatternConfig(
            count = prefs(context).getInt(KEY_SHAKE_PATTERN, 3),
            level = prefs(context).getInt(KEY_SHAKE_LEVEL, LEVEL_2ND)
        )

    fun setShakePattern(context: Context, config: PatternConfig) {
        prefs(context).edit()
            .putInt(KEY_SHAKE_PATTERN, config.count)
            .putInt(KEY_SHAKE_LEVEL, config.level)
            .apply()
    }

    fun isTelegramConfigured(context: Context): Boolean =
        getTelegramBotToken(context) != null && getTelegramChatIds(context).isNotEmpty()

    data class EmailConfig(
        val host: String = "",
        val port: Int = 587,
        val user: String = "",
        val password: String = "",
        val from: String = "",
        val recipient: String = ""
    )

    fun getEmailConfig(context: Context): EmailConfig {
        val prefs = prefs(context)
        return EmailConfig(
            host = prefs.getString(KEY_SMTP_HOST, "") ?: "",
            port = prefs.getInt(KEY_SMTP_PORT, 587),
            user = prefs.getString(KEY_SMTP_USER, "") ?: "",
            password = prefs.getString(KEY_SMTP_PASS, "") ?: "",
            from = prefs.getString(KEY_SMTP_FROM, "") ?: "",
            recipient = prefs.getString(KEY_EMAIL_TO, "") ?: ""
        )
    }

    fun setEmailConfig(context: Context, config: EmailConfig) {
        prefs(context).edit()
            .putString(KEY_SMTP_HOST, config.host.trim())
            .putInt(KEY_SMTP_PORT, config.port)
            .putString(KEY_SMTP_USER, config.user.trim())
            .putString(KEY_SMTP_PASS, config.password.trim())
            .putString(KEY_SMTP_FROM, config.from.trim())
            .putString(KEY_EMAIL_TO, config.recipient.trim())
            .apply()
    }

    fun isEmailConfigured(context: Context): Boolean {
        val cfg = getEmailConfig(context)
        return cfg.host.isNotBlank() && cfg.user.isNotBlank() &&
            cfg.password.isNotBlank() && cfg.recipient.isNotBlank()
    }

    private const val KEY_SCREEN_CAPTURE = "screen_capture_enabled"

    fun isScreenCaptureEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCREEN_CAPTURE, false)

    fun setScreenCaptureEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCREEN_CAPTURE, enabled).apply()
    }

    private const val KEY_CONSENTED = "consented"

    fun hasConsented(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONSENTED, false)

    fun setConsented(context: Context) {
        prefs(context).edit().putBoolean(KEY_CONSENTED, true).apply()
    }

    fun getUserId(context: Context): String {
        val prefs = prefs(context)
        val existing = prefs.getString(KEY_USER_ID, null)
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, fresh).apply()
        return fresh
    }

    fun isLostMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOST_MODE, false)

    fun setLostMode(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOST_MODE, active).apply()
    }

    /** Telegram getUpdates 오프셋 (보호자 명령 폴링용). */
    fun getTelegramUpdateOffset(context: Context): Long =
        prefs(context).getLong("tg_update_offset", 0L)

    fun setTelegramUpdateOffset(context: Context, offset: Long) {
        prefs(context).edit().putLong("tg_update_offset", offset).apply()
    }

    fun isSharingActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHARING_ACTIVE, false)

    fun setSharingActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHARING_ACTIVE, active).apply()
    }

    fun setLastLocation(context: Context, lat: Double, lng: Double) {
        prefs(context).edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LNG, lng.toFloat())
            .apply()
    }

    fun lastLat(context: Context): Double =
        prefs(context).getFloat(KEY_LAST_LAT, 0f).toDouble()

    fun lastLng(context: Context): Double =
        prefs(context).getFloat(KEY_LAST_LNG, 0f).toDouble()

    const val LEVEL_1ST = 1
    const val LEVEL_2ND = 2
}

data class PatternConfig(
    val count: Int = 3,
    val level: Int = UserPrefs.LEVEL_1ST
)