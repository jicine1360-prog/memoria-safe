package com.memoria.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memoria.R
import com.memoria.model.EmergencyEvent
import com.memoria.util.Encryption
import java.util.*

class MotionService : android.app.Service(), SensorEventListener, LocationListener {

    companion object {
        private const val CHANNEL_ID = "motion_service_channel"
        private const val NOTIFICATION_ID = 2
        private const val FALL_VELOCITY_THRESHOLD = 2.5f
        private const val IMPACT_THRESHOLD = 1.5f
        private const val MOVEMENT_THRESHOLD = 5.0f
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyro: Sensor? = null
    private var locationManager: LocationManager? = null
    private var currentLocation: Location? = null
    private var lastLocation: Location? = null
    private var motionState = MotionState.NORMAL
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeSensors()
        initializeLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        locationManager?.removeUpdates(this)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Motion Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Memoria")
            .setContentText("Monitoring movement and orientation")
            .setSmallIcon(R.drawable.ic_stat_motion)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager?.registerListener(
            this,
            gyro,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun initializeLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) 
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    10f,
                    this
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        when (e.sensor) {
            accelerometer -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                
                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z).toFloat()
                
                handleAcceleration(magnitude, e.timestamp)
            }
            gyro -> {
                // Handle rotation detection
                val rotationSpeed = kotlin.math.sqrt(
                    e.values[0] * e.values[0] +
                    e.values[1] * e.values[1] +
                    e.values[2] * e.values[2]
                ).toFloat()
                
                if (rotationSpeed > 2.0f) {
                    handleRotation(rotationSpeed)
                }
            }
        }
    }

    private fun handleAcceleration(magnitude: Float, timestamp: Long) {
        when (motionState) {
            MotionState.NORMAL -> {
                if (magnitude > 1.5f && magnitude < 2.5f) {
                    motionState = MotionState.FALLING
                }
            }
            MotionState.FALLING -> {
                if (magnitude > 3.0f) {
                    motionState = MotionState.FALL_IMPACT
                    handler.postDelayed({
                        if (motionState == MotionState.FALL_IMPACT) {
                            triggerEmergency("fall_detected", 0.9f)
                        }
                        motionState = MotionState.NORMAL
                    }, 500)
                }
            }
            MotionState.FALL_IMPACT -> {
                // Reset after detection
            }
        }
    }

    private fun handleRotation(rotationSpeed: Float) {
        if (rotationSpeed > 3.0f) {
            triggerEmergency("sudden_movement", 0.7f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        lastLocation = currentLocation
        currentLocation = location
        
        // Check for location anomalies
        val last = lastLocation
        if (last != null) {
            val distance = location.distanceTo(last) / 1000f // km
            val timeDiff = (location.time - last.time) / 1000f // seconds
            
            if (timeDiff > 0) {
                val speed = distance / timeDiff // km/s
                if (speed > MOVEMENT_THRESHOLD) {
                    triggerEmergency("rapid_movement", 0.8f)
                }
            }
        }
    }

    private fun triggerEmergency(type: String, confidence: Float) {
        val event = EmergencyEvent(
            id = null,
            timestamp = System.currentTimeMillis(),
            type = type,
            confidence = confidence,
            location = currentLocation?.let {
                EmergencyEvent.Location(it.latitude, it.longitude, it.accuracy)
            } ?: EmergencyEvent.Location(0.0, 0.0, 0.0f),
            audioPath = null,
            photoPath = null,
            telemetry = getTelemetry()
        )
        
        event.save()
        EmergencyResponder.triggerEmergency(event)
    }

    private fun getTelemetry(): Map<String, Any> {
        return mapOf(
            "battery" to getBatteryLevel(),
            "speed" to (currentLocation?.speed ?: 0f),
            "motion_state" to motionState.name
        )
    }

    private fun getBatteryLevel(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        return batteryStatus?.getIntExtra("level", -1) ?: 0
    }
}

enum class MotionState {
    NORMAL, FALLING, FALL_IMPACT
}
