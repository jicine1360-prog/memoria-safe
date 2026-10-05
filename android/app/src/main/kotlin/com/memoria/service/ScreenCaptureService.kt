package com.memoria.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.memoria.R
import com.memoria.ui.EmergencyActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaProjection 기반 화면 캡처 서비스.
 *
 * Android 14+에서는 MediaProjection을 백그라운드에서 임의로 시작할 수 없고
 * 사용자 동의(설정 화면의 "화면 캡처 허용" 버튼)가 필요하다.
 * 동의를 받은 후 이 서비스가 VirtualDisplay + ImageReader를 유지하며
 * 최신 프레임을 JPEG로 저장해, 감지 시점의 화면 스틸을 떠낸다.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 7
        private const val TAG = "MemoriaScreen"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private var instance: ScreenCaptureService? = null

        fun isRunning(): Boolean = instance != null

        /** 결과 코드와 인텐트를 서비스로 전달해 실제 캡처 준비를 시작한다. */
        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        /** 현재 매핑 중인 최신 화면 스틸을 파일로 저장한다. 없으면 null. */
        fun captureLatest(context: Context): File? = instance?.grabLatestFrame()

        /** 서비스를 중지하고 프로젝션을 정리한다. */
        fun stopCapture(context: Context) {
            instance?.let { context.stopService(Intent(context, ScreenCaptureService::class.java)) }
        }
    }

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionHandlerThread: HandlerThread? = null
    private var projectionHandler: Handler? = null

    @Volatile private var latestJpeg: ByteArray? = null
    @Volatile private var ready = false
    private var width = 1
    private var height = 1
    private var densityDpi = 160

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        instance = this
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data: Intent? = if (resultCode != -1) intent?.getParcelableExtra(EXTRA_RESULT_DATA) else null

        if (data != null && mediaProjection == null) {
            setupProjection(resultCode, data)
        } else if (data == null) {
            Log.w(TAG, "No projection data provided")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (instance === this) instance = null
        tearDownProjection()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ setup

    private fun startForegroundCompat() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        projectionHandlerThread = HandlerThread("memoria-screen").also {
            it.start()
        }
        projectionHandler = projectionHandlerThread?.looper?.let { Handler(it) }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                bmp.recycle()

                val out = java.io.ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 82, out)
                cropped.recycle()
                latestJpeg = out.toByteArray()
                ready = true
            } catch (e: Exception) {
                Log.e(TAG, "frame capture failed", e)
            } finally {
                image.close()
            }
        }, projectionHandler)

        imageReader = reader
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MemoriaScreenCapture",
                width, height, densityDpi,
                0,
                reader.surface,
                null,
                projectionHandler
            )
            Log.i(TAG, "projection started ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "projection failed", e)
            stopSelf()
        }
    }

    private fun tearDownProjection() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        projectionHandlerThread?.quitSafely()
        projectionHandlerThread = null
        projectionHandler = null
        latestJpeg = null
        ready = false
    }

    // ---------------------------------------------------------------- capture

    private fun grabLatestFrame(): File? {
        val jpeg = latestJpeg ?: return null
        return try {
            val dir = File(filesDir, "evidence")
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "screen_$stamp.jpg")
            FileOutputStream(file).use { it.write(jpeg) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "save frame failed", e)
            null
        }
    }

    // ------------------------------------------------------------- notification

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen capture",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, EmergencyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setContentTitle("Memoria Screen Capture")
            .setContentText("화면 캡처 대기 중")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }
}