package com.memoria.util

object AudioAnalyzer {
    
    private const val SCREAM_THRESHOLD = 75.0f
    private const val SHOUT_THRESHOLD = 65.0f
    private val SOS_KEYWORDS = listOf("help", "sos", "emergency", "danger")
    
    fun analyze(samples: IntArray): AudioAnalysis {
        val amplitude = calculateAmplitude(samples)
        val noiseLevel = calculateNoiseLevel(samples)
        val hasSOS = detectSOSKeywords()
        
        val isEmergency = (amplitude > SCREAM_THRESHOLD) ||
                         (noiseLevel > SCREAM_THRESHOLD) ||
                         hasSOS
        
        val confidence = when {
            amplitude > SCREAM_THRESHOLD && hasSOS -> 0.95f
            amplitude > SHOUT_THRESHOLD || noiseLevel > SHOUT_THRESHOLD -> 0.75f
            else -> 0.5f
        }
        
        return AudioAnalysis(
            isEmergency = isEmergency,
            confidence = confidence,
            amplitude = amplitude,
            noiseLevel = noiseLevel
        )
    }
    
    private fun calculateAmplitude(samples: IntArray): Float {
        var sum = 0.0
        for (sample in samples) {
            sum += sample * sample
        }
        return (Math.sqrt(sum / samples.size) / 32768.0 * 100).toFloat()
    }
    
    private fun calculateNoiseLevel(samples: IntArray): Float {
        var sum = 0.0
        for (i in 1 until samples.size) {
            val diff = kotlin.math.abs(samples[i] - samples[i - 1])
            sum += diff
        }
        return (sum / samples.size / 32768.0 * 100).toFloat()
    }
    
    private fun detectSOSKeywords(): Boolean {
        // Would analyze audio stream for SOS keywords
        return false
    }
    
    fun getNoiseLevel(): Int {
        return 0 // Current noise level in dB
    }
}

data class AudioAnalysis(
    val isEmergency: Boolean,
    val confidence: Float,
    val amplitude: Float,
    val noiseLevel: Float
)
