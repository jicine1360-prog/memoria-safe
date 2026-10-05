package com.memoria.util

/**
 * Detects user-defined tap/shake emergency patterns from accelerometer data.
 *
 * The algorithm is intentionally simple and tunable so it can be calibrated
 * on-device via the settings "test" flow:
 *
 *  - TAP: a short sharp spike in acceleration magnitude (). Once a spike is
 *    detected, further spikes within a debounce window are ignored.
 *  - SHAKE: rapid back-and-forth motion. Each zero/peak crossing above a
 *    threshold increments a "cycle" count; when enough cycles happen inside a
 *    time window a shake is registered.
 *
 * The detector accumulates occurrences of the chosen gesture and fires a
 * callback when the configured count is reached.
 */
class PatternDetector(private val listener: Listener) {

    interface Listener {
        fun onPatternDetected(kind: PatternKind, actualCount: Int)
        fun onProgress(kind: PatternKind, current: Int, target: Int)
    }

    enum class PatternKind { TAP, SHAKE }

    /** Tuned defaults; adjusted in memory during a settings test session. */
    var tapThreshold = 12.5f          // magnitude above which a tap is accepted
    var shakeThreshold = 11.0f        // magnitude peaks must exceed this
    var targetCount = 3               // how many gestures form a complete pattern
    var debounceMs = 400L             // ignore gestures within this window of the last one
    var tapDropMs = 500L              // max time a single tap spike may take to return
    var patternWindowMs = 1_500L      // max time between first and last gesture of a pattern
    var mode: PatternKind = PatternKind.TAP

    private var tapCandidateActive = false
    private var tapCandidateStart = 0L
    private var lastGestureAt = 0L
    private var patternStartAt = 0L
    private var gestureCount = 0
    private var shakePeakSign = 0
    private var shakeCycleStart = 0L
    private var shakeCycles = 0
    private var on = false

    private val GRAVITY = 9.81f

    /** Start a fresh detection window. Call before feeding samples (test mode). */
    fun reset() {
        tapCandidateActive = false
        // Begin with a far-past reference time so the very first gesture is
        // accepted by the debounce check (now - lastGestureAt >= debounceMs).
        lastGestureAt = Long.MIN_VALUE / 2
        patternStartAt = 0L
        gestureCount = 0
        shakePeakSign = 0
        shakeCycleStart = 0L
        shakeCycles = 0
        on = true
    }

    fun stop() {
        on = false
    }

    /** Feed one accelerometer measurement. `tsMs` is a monotonic clock in ms. */
    fun onSensor(x: Float, y: Float, z: Float, tsMs: Long) {
        if (!on) return
        val mag = kotlin.math.sqrt(x * x + y * y + z * z).toFloat()
        when (mode) {
            PatternKind.TAP -> detectTap(mag, tsMs)
            PatternKind.SHAKE -> detectShake(mag, tsMs)
        }
    }

    private fun detectTap(mag: Float, tsMs: Long) {
        val now = tsMs
        if (!tapCandidateActive) {
            if (mag > tapThreshold) {
                // New spike: only count it if enough time passed since previous gesture.
                if (now - lastGestureAt >= debounceMs) {
                    tapCandidateActive = true
                    tapCandidateStart = now
                }
            }
            return
        }

        // Candidate active: if magnitude drops back near gravity, seal the tap.
        if (mag < tapThreshold - 1.5f) {
            val elapsed = now - tapCandidateStart
            if (elapsed in 20L..tapDropMs) {
                registerGesture(PatternKind.TAP, now)
            }
            tapCandidateActive = false
        }

        if (now - tapCandidateStart > tapDropMs) {
            tapCandidateActive = false
        }
    }

    private fun detectShake(mag: Float, tsMs: Long) {
        val now = tsMs
        if (now - shakeCycleStart > 300L) {
            shakeCycleStart = now
            shakeCycles = 0
            shakePeakSign = 0
        }

        val rising = mag > shakeThreshold
        val falling = mag < GRAVITY - 0.5f

        val sign = when {
            rising -> 1
            falling -> -1
            else -> 0
        }

        if (sign != 0 && sign != shakePeakSign) {
            // Sign change => a half-cycle completed. Two half-cycles = one full shake.
            shakePeakSign = sign
            shakeCycles++
            if (shakeCycles >= 2) {
                registerGesture(PatternKind.SHAKE, now)
                shakeCycles = 0
                shakePeakSign = 0
            }
        }
    }

    private fun registerGesture(kind: PatternKind, nowMs: Long) {
        if (gestureCount == 0) {
            patternStartAt = nowMs
        } else if (nowMs - patternStartAt > patternWindowMs) {
            // Window expired before pattern completed; start fresh.
            gestureCount = 0
            patternStartAt = nowMs
        }

        gestureCount++
        lastGestureAt = nowMs
        listener.onProgress(kind, gestureCount, targetCount)

        if (gestureCount >= targetCount) {
            listener.onPatternDetected(kind, gestureCount)
            gestureCount = 0
            patternStartAt = 0L
        }
    }
}