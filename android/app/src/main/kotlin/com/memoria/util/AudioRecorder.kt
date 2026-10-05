package com.memoria.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

class AudioRecorder {

    private val sampleRate = 16000
    private var recorder: AudioRecord? = null
    private var started = false

    private fun ensureRecorder(): AudioRecord? {
        recorder?.let { return it }
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                (minBuffer * 2).coerceAtLeast(maxOf(minBuffer, 2048))
            )
            recorder = record
            record.startRecording()
            started = true
            record
        } catch (e: Exception) {
            null
        }
    }

    fun getLevel(): Float {
        val record = ensureRecorder() ?: return 0f
        if (!started) {
            return 0f
        }
        return try {
            val buffer = ShortArray(1024)
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) return 0f
            var sum = 0.0
            for (i in 0 until read) {
                sum += buffer[i] * buffer[i]
            }
            val rms = sqrt(sum / read)
            (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            0f
        }
    }

    fun release() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
        started = false
    }
}