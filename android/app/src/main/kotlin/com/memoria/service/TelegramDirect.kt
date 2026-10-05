package com.memoria.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import com.memoria.util.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelegramDirect {

    private const val API_BASE = "https://api.telegram.org/bot"
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1_500L

    private fun botToken(context: Context): String? = UserPrefs.getTelegramBotToken(context)

    private fun chatIds(context: Context): List<String> = UserPrefs.getTelegramChatIds(context)

    /** 모든 받는 사람에게 순차 발송한다. 한 명이라도 실패하면 false. */
    private suspend fun sendToAll(context: Context, block: suspend (chat: String) -> Boolean): Boolean {
        val ids = chatIds(context)
        if (ids.isEmpty()) return false
        var allOk = true
        for (chat in ids) {
            if (!block(chat)) allOk = false
        }
        return allOk
    }

    private fun apiUrl(token: String, method: String): String = "$API_BASE$token/$method"

    /** Sends a level-1 or level-2 emergency alert directly to the configured Telegram chat. */
    suspend fun sendEmergency(
        context: Context,
        level: Int,
        type: String,
        confidence: Float,
        location: Location?
    ): Boolean {
        val message = buildMessage(context, level, type, confidence, location)

        var sent = false
        for (attempt in 1..MAX_ATTEMPTS) {
            sent = sendText(context, message)
            if (sent) break
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }

        if (!sent) return false

        if (location != null) {
            repeat(MAX_ATTEMPTS) {
                if (sendLocation(context, location)) return@repeat
                delay(RETRY_DELAY_MS)
            }
            sendMapLink(context, location)
        }
        return true
    }

    /** Sends a standalone text message (used by the settings screen test button). */
    suspend fun sendTest(context: Context): Boolean {
        val text = "✅ Memoria 테스트 메시지 수신 확인\n" +
            "시간: ${now()}\n" +
            "설정이 정상적으로 연동되었습니다."
        return sendToAll(context) { chat ->
            sendMessage(context, chat, text, disablePreview = true)
        }
    }

    /**
     * 증거 파일(음성 WAV, 화면 캡처 JPEG, 경로 KML)을 텔레그램으로 전송한다.
     * 파일이 없거나 전송 실패해도 false를 반환할 뿐이며 예외를 던지지 않는다.
     */
    suspend fun sendEvidenceFile(
        context: Context,
        file: java.io.File,
        note: String
    ): Boolean {
        val token = botToken(context) ?: return false
        val ids = chatIds(context)
        if (ids.isEmpty()) return false
        var allOk = true
        for (chat in ids) {
            val url = apiUrl(token, "sendDocument")
            val params = mapOf(
                "chat_id" to chat,
                "caption" to note,
                "parse_mode" to "HTML"
            )
            if (!postMultipart(url, params, "document", file)) allOk = false
        }
        return allOk
    }

    private suspend fun postMultipart(
        url: String,
        params: Map<String, String>,
        fileField: String,
        file: java.io.File
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val boundary = "MemoriaBoundary" + System.currentTimeMillis().toString(16)
                val filename = file.name
                val mime = mimeFor(filename)

                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 20000
                    connection.readTimeout = 30000
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary=$boundary"
                    )

                    val code = try {
                        connection.outputStream.use { out ->
                            writeTextPart(out, boundary, "chat_id", params["chat_id"] ?: "")
                            writeTextPart(out, boundary, "parse_mode", params["parse_mode"] ?: "")
                            writeTextPart(out, boundary, "caption", params["caption"] ?: "")

                            out.write("--$boundary\r\n".toByteArray())
                            out.write(
                                "Content-Disposition: form-data; name=\"$fileField\"; filename=\"$filename\"\r\n".toByteArray()
                            )
                            out.write("Content-Type: $mime\r\n\r\n".toByteArray())
                            file.inputStream().use { it.copyTo(out) }
                            out.write("\r\n".toByteArray())
                            out.write("--$boundary--\r\n".toByteArray())
                            out.flush()
                        }
                        connection.responseCode
                    } catch (e: Exception) {
                        return@withContext false
                    }

                    if (code in 200..299) {
                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                        try {
                            JSONObject(text).optBoolean("ok", false)
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun writeTextPart(
        out: java.io.OutputStream,
        boundary: String,
        name: String,
        value: String
    ) {
        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray()
        )
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray())
    }

    private fun mimeFor(filename: String): String = when {
        filename.endsWith(".wav", ignoreCase = true) -> "audio/wav"
        filename.endsWith(".jpg", ignoreCase = true) || filename.endsWith(".jpeg", ignoreCase = true) ->
            "image/jpeg"
        filename.endsWith(".kml", ignoreCase = true) -> "application/vnd.google-earth.kml+xml"
        else -> "application/octet-stream"
    }

    private fun buildMessage(
        context: Context,
        level: Int,
        type: String,
        confidence: Float,
        location: Location?
    ): String {
        val (tag, header) = when (level) {
            UserPrefs.LEVEL_1ST ->
                "⚠️ [1차 경계]" to "비상 신호가 감지되었습니다. 확인이 필요합니다."
            UserPrefs.LEVEL_2ND ->
                "🚨 [2차 긴급]" to "비상 신호가 감지되었습니다. 즉시 도움이 필요합니다!"
            else ->
                "⚠️ [1차 경계]" to "비상 신호가 감지되었습니다."
        }

        val sb = StringBuilder()
        sb.append(tag).append("\n")
        sb.append(header).append("\n")
        sb.append("감지 유형: ").append(typeDescription(type)).append("\n")
        sb.append("신뢰도: ").append(String.format(Locale.US, "%.0f%%", confidence * 100)).append("\n")
        sb.append("시간: ").append(now()).append("\n")
        sb.append("배터리: ").append(batteryLevel(context)).append("%").append("\n")
        if (level == UserPrefs.LEVEL_2ND) {
            sb.append("\n<b>자세한 위치 및 증거 수집이 진행 중입니다.</b>")
        }
        location?.let {
            sb.append("\n위치: ").append(String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude))
        }
        return sb.toString()
    }

    private fun typeDescription(type: String): String = when (type) {
        "tap_pattern" -> "손가락 패턴 (톡톡)"
        "shake_pattern" -> "흔들기 패턴"
        "sos_activated" -> "SOS 버튼"
        "fall_detected" -> "낙상 감지"
        "rapid_movement" -> "급격한 움직임"
        "sudden_movement" -> "급격한 움직임"
        else -> type
    }

    private suspend fun sendText(context: Context, text: String): Boolean =
        sendToAll(context) { chat ->
            sendMessage(context, chat, text, disablePreview = true)
        }

    private suspend fun sendMessage(
        context: Context,
        chat: String,
        text: String,
        disablePreview: Boolean
    ): Boolean {
        val token = botToken(context) ?: return false
        val url = apiUrl(token, "sendMessage")
        val params = mapOf(
            "chat_id" to chat,
            "text" to text,
            "parse_mode" to "HTML",
            "disable_web_page_preview" to if (disablePreview) "true" else "false"
        )
        return postForm(url, params)
    }

    private suspend fun sendLocation(context: Context, location: Location): Boolean {
        val token = botToken(context) ?: return false
        return sendToAll(context) { chat ->
            val url = apiUrl(token, "sendLocation")
            val params = mapOf(
                "chat_id" to chat,
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString()
            )
            postForm(url, params)
        }
    }

    private suspend fun sendMapLink(context: Context, location: Location): Boolean {
        val token = botToken(context) ?: return false
        return sendToAll(context) { chat ->
            val url = apiUrl(token, "sendMessage")
            val q = URLEncoder.encode("${location.latitude},${location.longitude}", "UTF-8")
            val mapsLink = "https://maps.google.com/?q=$q"
            val text = "<a href=\"$mapsLink\">📍 지도에서 열기</a>"
            val params = mapOf(
                "chat_id" to chat,
                "text" to text,
                "parse_mode" to "HTML"
            )
            postForm(url, params)
        }
    }

    private suspend fun postForm(url: String, params: Map<String, String>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")

                    val body = params.entries.joinToString("&") { (k, v) ->
                        "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                    }
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                    val code = connection.responseCode
                    if (code in 200..299) {
                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                        try {
                            JSONObject(text).optBoolean("ok", false)
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun batteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun now(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
}