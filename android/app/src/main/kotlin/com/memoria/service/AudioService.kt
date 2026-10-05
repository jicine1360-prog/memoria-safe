package com.memoria.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memoria.R
import com.memoria.model.EmergencyEvent
import com.memoria.util.AudioAnalyzer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class AudioService : android.app.Service() {

    companion object {
        private const val CHANNEL_ID = "audio_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val BUFFER_SIZE_MS = 30000L
        private const val SAMPLE_RATE = 16000
    }

    private var audioRecord: AudioRecord? = null
    private var circularBuffer: CircularAudioBuffer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var recording = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeAudio()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            startRecording()
        } else if (intent?.action == "STOP") {
            stopRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        stopRecording()
        audioRecord?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring for emergency sounds"
            }
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Memoria")
            .setContentText("Monitoring audio for emergencies")
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initializeAudio() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2
        )
        
        circularBuffer = CircularAudioBuffer(BUFFER_SIZE_MS, SAMPLE_RATE)
    }

    private fun startRecording() {
        if (recording) return
        recording = true
        
        executor.execute {
            try {
                audioRecord?.startRecording()
                
                while (recording) {
                    val buffer = ByteArray(8192)
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        circularBuffer?.write(buffer, bytesRead)
                        analyzeAudioFrame(buffer, bytesRead)
                    }
                }
                
                audioRecord?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        recording = false
    }

    private fun analyzeAudioFrame(frame: ByteArray, length: Int) {
        val samples = IntArray(length / 2)
        for (i in 0 until samples.size) {
            samples[i] = ((frame[i * 2 + 1].toInt() shl 8) or (frame[i * 2].toInt() and 0xFF))
        }
        
        val analysis = AudioAnalyzer.analyze(samples)
        
        if (analysis.isEmergency) {
            triggerEmergencyEvent("voice_detected", analysis.confidence)
        }
    }

    private fun triggerEmergencyEvent(type: String, confidence: Float) {
        val preEventAudio = circularBuffer?.readAll() ?: byteArrayOf()
        val audioFile = saveAudio(preEventAudio)
        
        val event = EmergencyEvent(
            id = null,
            timestamp = System.currentTimeMillis(),
            type = type,
            confidence = confidence,
            location = getCurrentLocation(),
            audioPath = audioFile.absolutePath,
            photoPath = null,
            telemetry = getTelemetry()
        )
        
        event.save()
        EmergencyResponder.triggerEmergency(event)
    }

    private fun getCurrentLocation(): EmergencyEvent.Location {
        return EmergencyEvent.Location(0.0, 0.0, 0.0f)
    }

    private fun getTelemetry(): Map<String, Any> {
        return mapOf(
            "battery" to getBatteryLevel(),
            "noise_level" to AudioAnalyzer.getNoiseLevel()
        )
    }

    private fun getBatteryLevel(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        return batteryStatus?.getIntExtra("level", -1) ?: 0
    }

    private fun saveAudio(data: ByteArray): File {
        val dir = File(filesDir, "audio")
        dir.mkdirs()
        val file = File.createTempFile("audio_", ".wav", dir)
        
        FileOutputStream(file).use { out ->
            out.write(createWavHeader(data.size))
            out.write(data)
        }
        
        return file
    }

    private fun createWavHeader(dataSize: Int): ByteArray {
        val totalSize = 36 + dataSize
        return byteArrayOf(
            0x52, 0x49, 0x46, 0x46,
            (totalSize and 0xFF).toByte(),
            ((totalSize shr 8) and 0xFF).toByte(),
            ((totalSize shr 16) and 0xFF).toByte(),
            ((totalSize shr 24) and 0xFF).toByte(),
            0x57, 0x41, 0x56, 0x45,
            0x66, 0x6D, 0x74, 0x20,
            0x10, 0x00, 0x00, 0x00,
            0x01, 0x00,
            0x01, 0x00,
            (SAMPLE_RATE and 0xFF).toByte(),
            ((SAMPLE_RATE shr 8) and 0xFF).toByte(),
            ((SAMPLE_RATE shr 16) and 0xFF).toByte(),
            ((SAMPLE_RATE shr 24) and 0xFF).toByte(),
            (SAMPLE_RATE * 2 and 0xFF).toByte(),
            ((SAMPLE_RATE * 2 shr 8) and 0xFF).toByte(),
            ((SAMPLE_RATE * 2 shr 16) and 0xFF).toByte(),
            ((SAMPLE_RATE * 2 shr 24) and 0xFF).toByte(),
            0x02, 0x00,
            16, 0x00,
            0x64, 0x61, 0x74, 0x61,
            (dataSize and 0xFF).toByte(),
            ((dataSize shr 8) and 0xFF).toByte(),
            ((dataSize shr 16) and 0xFF).toByte(),
            ((dataSize shr 24) and 0xFF).toByte()
        )
    }
}

class CircularAudioBuffer(private val durationMs: Long, private val sampleRate: Int) {
    private val sampleSize = 2
    private val totalSamples = (durationMs * sampleRate / 1000).toInt()
    private val buffer = ByteArray(totalSamples * sampleSize)
    private var writePosition = 0
    private var filledSamples = 0

    fun write(data: ByteArray, length: Int) {
        val samplesToWrite = length / sampleSize
        for (i in 0 until samplesToWrite) {
            val sampleIndex = (writePosition + i) % totalSamples
            val byteIndex = sampleIndex * sampleSize
            buffer[byteIndex] = data[i * sampleSize]
            buffer[byteIndex + 1] = data[i * sampleSize + 1]
        }
        writePosition = (writePosition + samplesToWrite) % totalSamples
        filledSamples = kotlin.math.min(filledSamples + samplesToWrite, totalSamples)
    }

    fun readAll(): ByteArray {
        val readLength = filledSamples * sampleSize
        val result = ByteArray(readLength)
        var offset = 0
        val firstPartLength = kotlin.math.min(readLength, (totalSamples - writePosition) * sampleSize)
        if (firstPartLength > 0) {
            System.arraycopy(buffer, writePosition * sampleSize, result, offset, firstPartLength)
            offset += firstPartLength
        }
        val secondPartLength = readLength - firstPartLength
        if (secondPartLength > 0) {
            System.arraycopy(buffer, 0, result, offset, secondPartLength)
        }
        return result
    }
}
