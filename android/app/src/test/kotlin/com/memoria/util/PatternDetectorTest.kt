package com.memoria.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternDetectorTest {

    private class Collector : PatternDetector.Listener {
        var detected = 0
        val events = mutableListOf<Pair<PatternDetector.PatternKind, Int>>()
        var lastProgress: Pair<Int, Int>? = null

        override fun onPatternDetected(kind: PatternDetector.PatternKind, actualCount: Int) {
            detected++
            events.add(kind to actualCount)
        }

        override fun onProgress(kind: PatternDetector.PatternKind, current: Int, target: Int) {
            lastProgress = current to target
        }
    }

    private fun feed(d: PatternDetector, z: Float, ts: Long) {
        d.onSensor(0f, 0f, z, ts)
    }

    private val REST = 9.81f

    @Test
    fun tapCountsThreeSpikesWithinWindow() {
        val collector = Collector()
        val detector = PatternDetector(collector).apply {
            mode = PatternDetector.PatternKind.TAP
            targetCount = 3
            reset()
        }

        var ts = 0L
        feed(detector, REST, ts); ts += 20
        feed(detector, REST + 5f, ts); ts += 20          // tap 1 start
        feed(detector, REST, ts); ts += 20               // tap 1 sealed
        ts = 500L
        feed(detector, REST, ts); ts += 20
        feed(detector, REST + 5f, ts); ts += 20          // tap 2
        feed(detector, REST, ts); ts += 20
        ts = 1_000L
        feed(detector, REST, ts); ts += 20
        feed(detector, REST + 5f, ts); ts += 20          // tap 3
        feed(detector, REST, ts)

        assertEquals("3 taps should have fired the pattern exactly once", 1, collector.detected)
        assertEquals(PatternDetector.PatternKind.TAP, collector.events[0].first)
    }

    @Test
    fun tapsTooCloseAreDebounced() {
        val collector = Collector()
        val detector = PatternDetector(collector).apply {
            mode = PatternDetector.PatternKind.TAP
            targetCount = 3
            reset()
        }

        var ts = 0L
        // Two spikes 100ms apart, both inside the debounce window => one tap.
        repeat(2) {
            feed(detector, REST, ts); ts += 20
            feed(detector, REST + 5f, ts); ts += 20
            feed(detector, REST, ts); ts += 20
        }
        ts = 800L
        feed(detector, REST + 5f, ts); ts += 20
        feed(detector, REST, ts)

        // Spikes 1+2 collapse into a single tap; spike 3 makes the second tap.
        assertEquals(0, collector.detected)
        assertEquals(2, collector.lastProgress?.first ?: 0)
    }

    @Test
    fun differentModeIgnoresShakeAsTaps() {
        // A shake should not count as taps with default tap thresholds.
        val collector = Collector()
        val detector = PatternDetector(collector).apply {
            mode = PatternDetector.PatternKind.TAP
            targetCount = 3
            reset()
        }

        var ts = 500L
        repeat(6) {
            feed(detector, if (it % 2 == 0) REST + 4.5f else REST - 5f, ts); ts += 40
        }
        // Large slow shakes do not produce tap-strength spikes.
        assertEquals(0, collector.detected)
    }

    @Test
    fun shakeCountsFullCycles() {
        val collector = Collector()
        val detector = PatternDetector(collector).apply {
            mode = PatternDetector.PatternKind.SHAKE
            targetCount = 3
            reset()
        }

        var ts = 0L
        // A full shake = one high excursion + one low excursion.
        repeat(6) {
            feed(detector, if (it % 2 == 0) REST + 4.5f else REST - 5f, ts); ts += 100
        }
        assertEquals(1, collector.detected)
        assertEquals(PatternDetector.PatternKind.SHAKE, collector.events[0].first)
    }

    @Test
    fun windowExpiryResetsPattern() {
        val collector = Collector()
        val detector = PatternDetector(collector).apply {
            mode = PatternDetector.PatternKind.TAP
            targetCount = 3
            reset()
        }

        var ts = 0L
        feed(detector, REST + 5f, ts); ts += 20
        feed(detector, REST, ts)                          // tap 1 (pattern start)
        ts = 4_000L                                       // window expired (default 1500ms)
        feed(detector, REST + 5f, ts); ts += 20
        feed(detector, REST, ts)                          // tap 2 (fresh window start)
        ts = 4_500L
        feed(detector, REST + 5f, ts); ts += 20
        feed(detector, REST, ts)                          // tap 3
        ts = 5_000L
        feed(detector, REST + 5f, ts); ts += 20
        feed(detector, REST, ts)                          // tap 4 => fresh window completes
        assertEquals(1, collector.detected)
    }
}