package com.memoria.data.model

import java.util.Date

enum class EventFilter {
    ALL, EMERGENCY, SAFETY
}

enum class EventStatus {
    SAFE, WARNING, EMERGENCY
}

data class Event(
    val id: String,
    val timestamp: Date,
    val status: EventStatus,
    val type: String,
    val location: Location?,
    val description: String
)

data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Date
)

data class AudioLevel(
    val level: Float,
    val timestamp: Date
)

data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean,
    val timestamp: Date
)

data class NetworkStatus(
    val signalStrength: Int,
    val networkType: String,
    val timestamp: Date
)
