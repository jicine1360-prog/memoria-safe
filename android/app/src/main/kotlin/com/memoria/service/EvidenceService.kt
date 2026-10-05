package com.memoria.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.memoria.R
import com.memoria.ui.EmergencyActivity
import com.memoria.util.PathRecorder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class EvidenceService : Service() {

    companion object {
        private const val CHANNEL_ID = "evidence_channel"
        private const val NOTIFICATION_ID = 6
        private const val TAG = "MemoriaEvidence"

        private const val SAMPLE_RATE = 16_000
        private const val BUFFER_MS = 30_000L
        private const val PRE_SECONDS = 10
        private const val POST_SECONDS = 10

        private var running = false
        private var instance: EvidenceService? = null

        fun start(context: Context) {
            if (running) return
            ContextCompat.startForegroundService(context, Intent(context, EvidenceService::class.java))
        }

        fun isRunning(): Boolean = running

        /** Captures emergency evidence. Returns the WAV file, or null on failure.
         *  Blocks up to POST_SECONDS seconds; call off the main thread. */
        fun capture(context: Context): File? {
            val svc = instance ?: return null
            return svc.captureAndSave()
        }

        /** Saves the running service's path history to KML. Returns the file or null. */
        fun saveCurrentPath(context: Context): File? {
            val svc = instance ?: return null
            return svc.savePathKml()
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var audioRecord: AudioRecord? = null
    private var circularBuffer: CircularAudioBuffer? = null
    private var recording = false

    private lateinit var locationManager: LocationManager
    private var pathRecorder = PathRecorder()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            pathRecorder.add(location.time, location.latitude, location.longitude, location.accuracy)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        running = true
        startForeground(NOTIFICATION_ID, buildNotification())
        initAudio()
        initLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null
        circularBuffer = null
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        executor.shutdown()
        if (instance === this) instance = null
        running = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------- audio

    private fun initAudio() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing")
            return
        }
        try {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            circularBuffer = CircularAudioBuffer(BUFFER_MS, SAMPLE_RATE)
            startLoop()
        } catch (e: Exception) {
            Log.e(TAG, "audio init failed", e)
        }
    }

    private fun startLoop() {
        recording = true
        executor.execute {
            try {
                audioRecord?.startRecording()
                while (recording) {
                    val buf = ByteArray(8192)
                    val read = audioRecord?.read(buf, 0, buf.size) ?: 0
                    if (read > 0) {
                        circularBuffer?.write(buf, read)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "audio loop ended", e)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Extracts the last 10 s of captured audio, records 10 s more, and returns a WAV file. */
    private fun captureAndSave(): File? {
        val buffer = circularBuffer ?: return null

        val pre = buffer.readAll().takeLast(PRE_SECONDS * SAMPLE_RATE * 2).toByteArray()

        // Record POST_SECONDS more seconds while continuing to fill the buffer,
        // then capture the latest window for the "after" clip.
        val deadline = System.currentTimeMillis() + POST_SECONDS * 1000L
        while (System.currentTimeMillis() < deadline) {
            android.os.SystemClock.sleep(50)
        }
        val post = buffer.readAll().takeLast(POST_SECONDS * SAMPLE_RATE * 2).toByteArray()

        val combined = ByteArray(pre.size + post.size)
        System.arraycopy(pre, 0, combined, 0, pre.size)
        System.arraycopy(post, 0, combined, pre.size, post.size)

        return saveWav(combined)
    }

    private fun saveWav(data: ByteArray): File? {
        return try {
            val dir = File(filesDir, "evidence")
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "evidence_$stamp.wav")
            FileOutputStream(file).use { out ->
                out.write(wavHeader(data.size))
                out.write(data)
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "wav save failed", e)
            null
        }
    }

    private fun wavHeader(dataSize: Int): ByteArray {
        val sampleRate = SAMPLE_RATE
        val byteRate = sampleRate * 2
        val total = 36 + dataSize
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (total and 0xFF).toByte()
        header[5] = ((total shr 8) and 0xFF).toByte()
        header[6] = ((total shr 16) and 0xFF).toByte()
        header[7] = ((total shr 24) and 0xFF).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = 1
        header[23] = 0
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        header[32] = 2
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xFF).toByte()
        header[41] = ((dataSize shr 8) and 0xFF).toByte()
        header[42] = ((dataSize shr 16) and 0xFF).toByte()
        header[43] = ((dataSize shr 24) and 0xFF).toByte()
        return header
    }

    // -------------------------------------------------------------- location

    private fun initLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 30_000L, 10f, locationListener
                )
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 30_000L, 10f, locationListener
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "location updates denied", e)
            }
        }
    }

    fun recentPath(): PathRecorder = pathRecorder

    /** Saves the last-hour path as a KML file (best-effort). Returns the file or null. */
    fun savePathKml(): File? {
        return try {
            val dir = File(filesDir, "evidence")
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "path_$stamp.kml")
            pathRecorder.saveTo(file)
            file
        } catch (e: Exception) {
            Log.e(TAG, "kml save failed", e)
            null
        }
    }

    // ------------------------------------------------------------ notification

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Evidence recording",
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
            .setContentTitle("Memoria Evidence")
            .setContentText("음성/경로 기록 중")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }
}