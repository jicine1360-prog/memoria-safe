package com.memoria.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import com.memoria.BuildConfig
import com.memoria.util.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

object TelegramReporter {

    private const val PATH_LOCATION = "/api/v1/telegram/location"
    private const val PATH_ALERT = "/api/v1/telegram/alert"
    private const val PATH_LOST = "/api/v1/telegram/lost"
    private const val PATH_STOP = "/api/v1/telegram/stop"
    private const val PATH_STATUS = "/api/v1/telegram/status/"
    private const val THROTTLE_MS = 30_000L

    private val lastLocationSent = AtomicLong(0L)
    private val lastAlertSent = AtomicLong(0L)

    private fun baseUrl(): String = BuildConfig.SERVER_BASE_URL.trimEnd('/')

    suspend fun reportLocation(context: Context, location: Location, source: String = "gps"): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLocationSent.get() < THROTTLE_MS) return false
        val battery = batteryLevel(context)
        val body = JSONObject()
            .put("user_id", UserPrefs.getUserId(context))
            .put("lat", location.latitude)
            .put("lng", location.longitude)
            .put("accuracy", location.accuracy)
            .put("battery", battery)
            .put("source", source)
        val ok = postJson(PATH_LOCATION, body)
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
        val body = JSONObject()
            .put("user_id", UserPrefs.getUserId(context))
            .put("severity", severity)
            .put("message", message)
        location?.let {
            body.put("lat", it.latitude)
            body.put("lng", it.longitude)
        }
        val ok = postJson(PATH_ALERT, body)
        if (ok) lastAlertSent.set(System.currentTimeMillis())
        return ok
    }

    suspend fun activateLost(context: Context, message: String? = null): Boolean {
        val body = JSONObject().put("user_id", UserPrefs.getUserId(context))
        message?.let { body.put("message", it) }
        return postJson(PATH_LOST, body)
    }

    suspend fun stopSharing(context: Context): Boolean {
        val body = JSONObject().put("user_id", UserPrefs.getUserId(context))
        return postJson(PATH_STOP, body)
    }

    suspend fun lostModeActive(context: Context): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val url = URL(baseUrl() + PATH_STATUS + UserPrefs.getUserId(context))
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(text).optBoolean("lost_mode", false)
                    } else {
                        false
                    }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun postJson(path: String, body: JSONObject): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val url = URL(baseUrl() + path)
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    code in 200..299
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun batteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}