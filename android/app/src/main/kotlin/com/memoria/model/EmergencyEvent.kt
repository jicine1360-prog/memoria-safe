package com.memoria.model

import androidx.room.*
import java.io.File

@Entity(tableName = "emergency_events")
data class EmergencyEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long?,

    val timestamp: Long,
    val type: String,
    val confidence: Float,
    val location: Location,
    val audioPath: String?,
    val photoPath: String?,
    val telemetry: Map<String, Any>,

    val status: String = "detected",
    val deviceId: String = genDeviceId()
) {

    data class Location(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float
    )

    fun save() {
        // Save to local database
    }

    fun getAudioFile(): File? {
        return audioPath?.let { File(it) }
    }

    fun getPhotoFile(): File? {
        return photoPath?.let { File(it) }
    }
}

fun genDeviceId(): String {
    return "device_${System.currentTimeMillis()}"
}