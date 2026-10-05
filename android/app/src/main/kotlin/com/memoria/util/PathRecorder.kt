package com.memoria.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 최근 1시간 경로를 추적한다.
 * add() 호출 시 windowMs 이외의 오래된 포인트는 제거된다.
 */
class PathRecorder(
    private val windowMs: Long = 60L * 60L * 1000L
) {
    data class Point(
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float
    )

    private val points = ArrayDeque<Point>()

    val size: Int get() = points.size

    fun add(timestamp: Long, latitude: Double, longitude: Double, accuracy: Float) {
        points.add(Point(timestamp, latitude, longitude, accuracy))
        prune()
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - windowMs
        while (points.isNotEmpty() && points.first().timestamp < cutoff) {
            points.removeFirst()
        }
    }

    fun clear() = points.clear()

    fun recent(): List<Point> = points.toList()

    fun toKml(): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        for (p in points) {
            sb.append("<Placemark><Point><coordinates>")
                .append(p.longitude).append(',').append(p.latitude).append(",0")
                .append("</coordinates></Point></Placemark>\n")
        }
        sb.append("</Document>\n</kml>\n")
        return sb.toString()
    }

    fun toPlainText(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        for (p in points) {
            sb.append(fmt.format(Date(p.timestamp)))
                .append(" http://maps.google.com/?q=")
                .append(p.latitude).append(',').append(p.longitude)
                .append(" (acc ").append(p.accuracy.toInt()).append("m)\n")
        }
        return sb.toString()
    }

    fun saveTo(file: File) {
        file.writeText(toKml())
    }
}