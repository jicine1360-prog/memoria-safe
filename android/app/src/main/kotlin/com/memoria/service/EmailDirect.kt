package com.memoria.service

import android.content.Context
import com.memoria.util.UserPrefs
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object EmailDirect {

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1_500L

    /** Sends an emergency alert email to the configured recipient via SMTP. */
    suspend fun sendEmergency(
        context: Context,
        level: Int,
        type: String,
        confidence: Float,
        location: android.location.Location?
    ): Boolean {
        val config = UserPrefs.getEmailConfig(context)
        if (!config.isValid()) return false

        val subject = when (level) {
            UserPrefs.LEVEL_1ST -> "⚠️ [Memoria 1차] 비상 신호 감지"
            UserPrefs.LEVEL_2ND -> "🚨 [Memoria 2차] 긴급 비상 신호 감지"
            else -> "[Memoria] 비상 신호 감지"
        }
        val body = buildBody(context, level, type, confidence, location)

        var sent = false
        for (attempt in 1..MAX_ATTEMPTS) {
            sent = withContext(Dispatchers.IO) {
                send(config, subject, body, location, emptyList())
            }
            if (sent) break
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }
        return sent
    }

    /** Sends a standalone test email from the settings screen. */
    suspend fun sendTest(context: Context): Boolean {
        val config = UserPrefs.getEmailConfig(context)
        if (!config.isValid()) return false
        val subject = "✅ [Memoria] 이메일 연동 테스트"
        val body = "시간: ${java.util.Date()}\n이메일 전송이 정상적으로 작동합니다."
        return withContext(Dispatchers.IO) {
            send(config, subject, body, location = null, attachments = emptyList())
        }
    }

    /** 증거 파일(음성 WAV, 화면 캡처 JPEG, 경로 KML)을 첨부해 비상 알림 이메일을 전송한다. */
    suspend fun sendEmergencyWithEvidence(
        context: Context,
        level: Int,
        type: String,
        confidence: Float,
        location: android.location.Location?,
        attachments: List<java.io.File>
    ): Boolean {
        val config = UserPrefs.getEmailConfig(context)
        if (!config.isValid()) return false

        val subject = when (level) {
            UserPrefs.LEVEL_1ST -> "⚠️ [Memoria 1차] 비상 신호 감지"
            UserPrefs.LEVEL_2ND -> "🚨 [Memoria 2차] 긴급 비상 신호 감지"
            else -> "[Memoria] 비상 신호 감지"
        }
        val body = buildBody(context, level, type, confidence, location)

        var sent = false
        for (attempt in 1..MAX_ATTEMPTS) {
            sent = withContext(Dispatchers.IO) {
                send(config, subject, body, location, attachments)
            }
            if (sent) break
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }
        return sent
    }

    private fun UserPrefs.EmailConfig.isValid(): Boolean =
        host.isNotBlank() && user.isNotBlank() && password.isNotBlank() &&
            recipient.isNotBlank() && from.isNotBlank()

    private fun buildBody(
        context: Context,
        level: Int,
        type: String,
        confidence: Float,
        location: android.location.Location?
    ): String {
        val sb = StringBuilder()
        sb.append("Memoria 비상 신호 알림\n")
        sb.append("==========================\n")
        sb.append("등급: ").append(if (level == UserPrefs.LEVEL_2ND) "2차 (긴급)" else "1차 (경계)").append("\n")
        sb.append("감지 유형: ").append(typeDescription(type)).append("\n")
        sb.append("신뢰도: ").append(String.format(java.util.Locale.US, "%.0f%%", confidence * 100)).append("\n")
        sb.append("시간: ").append(java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date())).append("\n")
        sb.append("기기: ").append(android.os.Build.MODEL).append("\n")
        location?.let {
            sb.append("\n위치 (위도, 경도): ")
                .append(String.format(java.util.Locale.US, "%.6f, %.6f", it.latitude, it.longitude)).append("\n")
            val q = java.net.URLEncoder.encode("${it.latitude},${it.longitude}", "UTF-8")
            sb.append("지도: https://maps.google.com/?q=").append(q).append("\n")
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

    private fun send(
        config: UserPrefs.EmailConfig,
        subject: String,
        body: String,
        location: android.location.Location?,
        attachments: List<java.io.File>
    ): Boolean {
        return try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", config.host)
                put("mail.smtp.port", config.port.toString())
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(config.user, config.password)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.recipient))
                this.subject = subject
            }

            if (attachments.isEmpty()) {
                message.setText(body, "utf-8")
            } else {
                val multipart = javax.mail.internet.MimeMultipart()
                val textPart = javax.mail.internet.MimeBodyPart()
                textPart.setText(body, "utf-8")
                multipart.addBodyPart(textPart)

                for (file in attachments) {
                    if (!file.exists()) continue
                    val attachPart = javax.mail.internet.MimeBodyPart()
                    attachPart.attachFile(file, file.name, null)
                    multipart.addBodyPart(attachPart)
                }
                message.setContent(multipart)
            }

            Transport.send(message)
            true
        } catch (e: Exception) {
            android.util.Log.e("MemoriaEmail", "SMTP send failed: ${e.message}")
            false
        }
    }
}