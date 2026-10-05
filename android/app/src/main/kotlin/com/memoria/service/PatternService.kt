package com.memoria.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.memoria.R
import com.memoria.ui.EmergencyActivity
import com.memoria.util.PatternDetector
import com.memoria.util.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class PatternService : Service(), SensorEventListener {

    interface TestListener {
        fun onProgress(kind: PatternDetector.PatternKind, current: Int, target: Int)
        fun onTestDetected(kind: PatternDetector.PatternKind)
        fun onCalibrationLevel(level: Int)
    }

    companion object {
        const val ACTION_START_TEST = "com.memoria.action.START_TEST"
        const val ACTION_STOP_TEST = "com.memoria.action.STOP_TEST"

        private const val CHANNEL_ID = "pattern_service_channel"
        private const val NOTIFICATION_ID = 5
        private const val TAG = "MemoriaPattern"
        private const val FULL_TRIGGER_COOLDOWN_MS = 30_000L

        private var monitoringStarted = false
        private var currentInstance: PatternService? = null

        var testListener: TestListener? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PatternService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PatternService::class.java))
        }

        /** Starts monitoring if Telegram is configured and the service is not already up. */
        fun ensureMonitoring(context: Context) {
            if (UserPrefs.isTelegramConfigured(context) && !monitoringStarted) {
                start(context)
            }
        }

        fun beginTest(kind: PatternDetector.PatternKind, target: Int) {
            currentInstance?.beginTestSession(kind, target)
        }

        fun endTest() {
            currentInstance?.endTestSession()
        }

        fun isMonitoring(): Boolean = monitoringStarted
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var triggerJob: Job? = null
    private var lastFullTriggerAt = 0L

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val tapDetector = PatternDetector(object : PatternDetector.Listener {
        override fun onPatternDetected(kind: PatternDetector.PatternKind, actualCount: Int) {
            handlePattern(kind, actualCount, isTest = false)
        }

        override fun onProgress(kind: PatternDetector.PatternKind, current: Int, target: Int) {
            testListener?.onProgress(kind, current, target)
        }
    })

    private val shakeDetector = PatternDetector(object : PatternDetector.Listener {
        override fun onPatternDetected(kind: PatternDetector.PatternKind, actualCount: Int) {
            handlePattern(kind, actualCount, isTest = false)
        }

        override fun onProgress(kind: PatternDetector.PatternKind, current: Int, target: Int) {
            testListener?.onProgress(kind, current, target)
        }
    })

    private var testDetector: PatternDetector? = null
    private var testMode = false

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        startForeground(NOTIFICATION_ID, buildNotification())
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        configureMonitoringDetectors()
        registerSensor()
        monitoringStarted = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TEST -> testMode = true
            ACTION_STOP_TEST -> endTestSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitoringStarted = false
        if (currentInstance === this) currentInstance = null
        unregisterSensor()
        testListener = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- sensor

    private fun registerSensor() {
        try {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        } catch (e: Exception) {
            Log.e(TAG, "sensor register failed", e)
        }
    }

    private fun unregisterSensor() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val nowMs = SystemClock.elapsedRealtime()
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]

        testDetector?.onSensor(x, y, z, nowMs)
        if (!testMode) {
            tapDetector.onSensor(x, y, z, nowMs)
            shakeDetector.onSensor(x, y, z, nowMs)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ----------------------------------------------------------------- setup

    private fun configureMonitoringDetectors() {
        val tap = UserPrefs.getTapPattern(this)
        val shake = UserPrefs.getShakePattern(this)
        tapDetector.mode = PatternDetector.PatternKind.TAP
        tapDetector.targetCount = tap.count
        tapDetector.reset()
        shakeDetector.mode = PatternDetector.PatternKind.SHAKE
        shakeDetector.targetCount = shake.count
        shakeDetector.reset()
    }

    // --------------------------------------------------------------- actions

    fun beginTestSession(kind: PatternDetector.PatternKind, target: Int) {
        testMode = true
        testDetector = PatternDetector(object : PatternDetector.Listener {
            override fun onPatternDetected(kind: PatternDetector.PatternKind, actualCount: Int) {
                testListener?.onTestDetected(kind)
            }

            override fun onProgress(kind: PatternDetector.PatternKind, current: Int, target: Int) {
                testListener?.onProgress(kind, current, target)
            }
        }).apply {
            mode = kind
            this.targetCount = target
            reset()
        }
    }

    fun endTestSession() {
        testMode = false
        testDetector = null
        configureMonitoringDetectors()
        testListener = null
    }

    private fun handlePattern(kind: PatternDetector.PatternKind, actualCount: Int, isTest: Boolean) {
        if (isTest) {
            testListener?.onTestDetected(kind)
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastFullTriggerAt < FULL_TRIGGER_COOLDOWN_MS) {
            Log.d(TAG, "Full trigger suppressed by cooldown")
            return
        }
        lastFullTriggerAt = now

        val (config, type) = when (kind) {
            PatternDetector.PatternKind.TAP ->
                UserPrefs.getTapPattern(this) to "tap_pattern"
            PatternDetector.PatternKind.SHAKE ->
                UserPrefs.getShakePattern(this) to "shake_pattern"
        }

        // Reset detectors so a single incident does not fire repeatedly.
        tapDetector.reset()
        shakeDetector.reset()

        triggerJob?.cancel()
        triggerJob = scope.launch {
            // 1) 즉시 긴급 알림 (텍스트 + 위치)
            val location = lastKnownLocation()
            val tgSent = TelegramDirect.sendEmergency(
                this@PatternService,
                level = config.level,
                type = type,
                confidence = 0.9f,
                location = location
            )
            // 이메일 전송은 보류(Telegram 중심). EmailDirect 코드는 유지하되 호출하지 않음.
            Log.i(TAG, "Pattern $kind triggered: tg=$tgSent level=${config.level}")

            // 2) 증거 수집: 전+후 10초 음성 + 최근 1시간 경로 KML + 화면 캡처
            EvidenceService.start(this@PatternService)
            val audioFile = EvidenceService.capture(this@PatternService)
            val pathFile = EvidenceService.saveCurrentPath(this@PatternService)
            val screenFile = if (UserPrefs.isScreenCaptureEnabled(this@PatternService)) {
                ScreenCaptureService.captureLatest(this@PatternService)
            } else {
                null
            }

            val evidenceNote = when (config.level) {
                UserPrefs.LEVEL_1ST -> "⚠️ [Memoria 1차] 증거"
                else -> "🚨 [Memoria 2차] 증거"
            }

            audioFile?.let { file ->
                val ok = TelegramDirect.sendEvidenceFile(
                    this@PatternService, file,
                    "$evidenceNote: 전후 10초 음성"
                )
                Log.i(TAG, "audio evidence tg: $ok")
            }
            pathFile?.let { file ->
                val ok = TelegramDirect.sendEvidenceFile(
                    this@PatternService, file,
                    "$evidenceNote: 최근 1시간 경로"
                )
                Log.i(TAG, "path evidence tg: $ok")
            }
            screenFile?.let { file ->
                val ok = TelegramDirect.sendEvidenceFile(
                    this@PatternService, file,
                    "$evidenceNote: 화면 캡처"
                )
                Log.i(TAG, "screen evidence tg: $ok")
            }

            // 증거 이메일 전송도 보류(Telegram 중심).
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).firstNotNullOfOrNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, EmergencyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_motion)
            .setContentTitle("Memoria Monitoring")
            .setContentText("탭/흔들기 패턴 감지 활성화")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pattern monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}