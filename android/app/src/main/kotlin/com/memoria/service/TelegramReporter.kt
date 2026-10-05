package com.memoria.service

import android.content.Context
import android.location.Location
import com.memoria.util.UserPrefs
import java.util.concurrent.atomic.AtomicLong

/**
 * 서버 없이 Telegram만으로 분실 모드와 위치 보고를 처리한다.
 * 보호자가 봇에 /lost, /stop 을 보내면 기기가 getUpdates로 명령을 조회해 상태를 바꾼다.
 */
object TelegramReporter {

    private const val THROTTLE_MS = 30_000L

    private val lastLocationSent = AtomicLong(0L)

    suspend fun reportLocation(
        context: Context,
        location: Location,
        source: String = "gps"
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLocationSent.get() < THROTTLE_MS) return false
        val ok = TelegramDirect.sendLocationUpdate(context, location, note = "📍 위치 공유 중")
        if (ok) {
            lastLocationSent.set(System.currentTimeMillis())
            UserPrefs.setLastLocation(context, location.latitude, location.longitude)
        }
        return ok
    }

    suspend fun reportAlert(
        context: Context,
        severity: String = "high",
        message: String,
        location: Location? = null
    ): Boolean {
        val ok = TelegramDirect.sendStatusText(context, "⚠️ $message")
        if (ok && location != null) TelegramDirect.sendLocationUpdate(context, location)
        return ok
    }

    suspend fun activateLost(context: Context, message: String? = null): Boolean {
        UserPrefs.setLostMode(context, true)
        return TelegramDirect.sendStatusText(
            context,
            "🚨 분실 모드가 켜졌습니다. 위치 공유를 시작합니다." + (message?.let { "\n$it" } ?: "")
        )
    }

    suspend fun stopSharing(context: Context): Boolean {
        UserPrefs.setLostMode(context, false)
        UserPrefs.setSharingActive(context, false)
        return TelegramDirect.sendStatusText(context, "🛑 위치 공유를 중지했습니다.")
    }

    /** 보호자 명령(/lost, /stop)을 조회해 분실 모드 상태를 갱신하고 현재 상태를 반환한다. */
    suspend fun lostModeActive(context: Context): Boolean {
        when (TelegramDirect.pollLostCommands(context)) {
            "lost" -> UserPrefs.setLostMode(context, true)
            "stop" -> {
                UserPrefs.setLostMode(context, false)
                UserPrefs.setSharingActive(context, false)
            }
        }
        return UserPrefs.isLostMode(context)
    }
}
