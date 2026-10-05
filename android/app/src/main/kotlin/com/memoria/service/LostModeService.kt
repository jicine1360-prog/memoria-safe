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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.memoria.R
import com.memoria.ui.LostModeActivity
import com.memoria.util.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LostModeService : Service(), LocationListener {

    companion object {
        private const val CHANNEL_ID = "lost_mode_channel"
        private const val NOTIFICATION_ID = 3
        private const val POLL_INTERVAL_MS = 30_000L
        private const val LOCATION_INTERVAL_MS = 15_000L
        private const val MIN_DISTANCE_M = 10f

        fun start(context: Context) {
            val intent = Intent(context, LostModeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LostModeService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var locationJob: Job? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var overlayShown = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startPolling()
        startLocationReporting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        locationJob?.cancel()
        scope.cancel()
        stopLocationUpdates()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, LostModeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_motion)
            .setContentTitle(getString(R.string.notif_lost_title))
            .setContentText(getString(R.string.notif_lost_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoring and lost mode",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                val lost = TelegramReporter.lostModeActive(baseContext)
                UserPrefs.setLostMode(baseContext, lost)
                if (lost) {
                    showPublicLostScreenIfNotShown()
                } else {
                    overlayShown = false
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun startLocationReporting() {
        askLocationUpdates()
        locationJob = scope.launch {
            while (isActive) {
                val loc = lastLocation ?: lastKnownLocation()
                if (loc != null) {
                    TelegramReporter.reportLocation(baseContext, loc)
                }
                delay(LOCATION_INTERVAL_MS)
            }
        }
    }

    private fun askLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                MIN_DISTANCE_M,
                this
            )
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_INTERVAL_MS,
                MIN_DISTANCE_M,
                this
            )
        } catch (e: SecurityException) {
            // ignore
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
        } catch (e: SecurityException) {
            // ignore
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                val loc = locationManager?.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (e: SecurityException) {
                // ignore
            }
        }
        return null
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        UserPrefs.setLastLocation(this, location.latitude, location.longitude)
    }

    private fun showPublicLostScreenIfNotShown() {
        if (overlayShown) return
        overlayShown = true

        val context = baseContext
        val intent = Intent(context, LostModeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val fullScreenIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = getSystemService(NotificationManager::class.java)
        val fullScreenNotification = NotificationCompat.Builder(context, buildFullScreenChannelId())
            .setSmallIcon(R.drawable.ic_stat_motion)
            .setContentTitle(getString(R.string.notif_lost_title))
            .setContentText(getString(R.string.notif_lost_fullscreen))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(false)
            .build()
        manager.notify(4, fullScreenNotification)
    }

    private fun buildFullScreenChannelId(): String {
        createChannel()
        return CHANNEL_ID
    }
}