package com.memoria.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.memoria.R
import com.memoria.data.model.Event
import com.memoria.data.model.EventFilter
import com.memoria.data.repository.EventRepository
import com.memoria.databinding.ActivityMainBinding
import com.memoria.ui.components.SOSButton
import com.memoria.ui.components.WaveformView
import com.memoria.util.AudioRecorder
import com.memoria.util.UserPrefs
import com.memoria.viewmodel.EmergencyViewModel
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: EmergencyViewModel
    private var toneGenerator: ToneGenerator? = null
    private var hapticHandler: Handler? = null
    private var sosButtonPressed = false
    private var sosTapCount = 0
    private val sosTapHandler = Handler(Looper.getMainLooper())
    private val sosLongPressHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserPrefs.hasConsented(this)) {
            startActivity(Intent(this, ConsentActivity::class.java))
            finish()
            return
        }

        // 보존기간이 지난 로컬 증거 파일 정리 (개인정보 보존 최소화)
        com.memoria.util.RetentionCleaner.clean(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_surface)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_surface)
        
        setupViewModel()
        setupPermissions()
        setupUI()
        setupWaveform()
        setupGPSStatus()
        setupBatteryStatus()
        setupNetworkStatus()
        setupQuickContacts()
        setupEventTimeline()
        setupPrivacyToggle()
        setupSettingsButton()
        
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        hapticHandler = Handler(Looper.getMainLooper())

        com.memoria.service.PatternService.ensureMonitoring(this)
    }

    override fun onStart() {
        super.onStart()
        startStatusUpdates()
    }

    override fun onStop() {
        super.onStop()
        stopStatusUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        hapticHandler?.removeCallbacksAndMessages(null)
    }

    private lateinit var adapter: EventAdapter

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[EmergencyViewModel::class.java]
        viewModel.events.observe(this) { events ->
            if (::adapter.isInitialized && events != null) {
                adapter.submitList(events)
            }
        }
    }

    private fun setupPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupUI() {
        setupSOSButton()
        setupPrivacyControls()
    }

    private fun setupSOSButton() {
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                handleTripleTap()
                return true
            }
        })

        binding.sosButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sosButtonPressed = true
                    hapticFeedback(HAPTIC_LEVEL_STRONG)
                    sosLongPressHandler?.postDelayed({
                        handleLongPress()
                    }, LONG_PRESS_DURATION)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sosButtonPressed = false
                    sosLongPressHandler?.removeCallbacksAndMessages(null)
                    sosTapHandler?.removeCallbacksAndMessages(null)
                    if (sosTapCount > 0 && !sosButtonPressed) {
                        sosTapCount = 0
                    }
                }
            }
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun handleTripleTap() {
        sosTapCount++
        if (sosTapCount == 3) {
            hapticFeedback(HAPTIC_LEVEL_STRONG)
            activateEmergencyMode()
            sosTapCount = 0
        } else {
            sosTapHandler?.postDelayed({
                if (sosTapCount < 3) {
                    sosTapCount = 0
                }
            }, TAP_TIMEOUT)
        }
    }

    private fun handleLongPress() {
        if (sosButtonPressed) {
            hapticFeedback(HAPTIC_LEVEL_STRONG)
            activateEmergencyMode()
        }
    }

    private fun setupPrivacyControls() {
        binding.privacyModeToggle.isChecked = false
        binding.privacyModeToggle.setOnCheckedChangeListener { _, isChecked ->
            handlePrivacyToggle(isChecked)
        }
    }

    private fun setupSettingsButton() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupWaveform() {
        binding.waveformView.startRecording()

        viewModel.audioLevel.observe(this) { level ->
            binding.waveformView.updateLevel(level)
        }
    }

    private fun setupGPSStatus() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        
        val locationUpdateHandler = object : Runnable {
            override fun run() {
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (hasFineLocation) {
                    val lastLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    val hasGpsFix = lastLocation != null && lastLocation.accuracy < 50f
                    
                    runOnUiThread {
                        binding.gpsStatus.setText(
                            if (hasGpsFix) R.string.gps_status_active else R.string.gps_status_acquiring
                        )
                        binding.gpsStatus.setCompoundDrawablesWithIntrinsicBounds(
                            if (hasGpsFix) R.drawable.ic_gps else R.drawable.ic_gps_acquiring,
                            0, 0, 0
                        )
                    }
                } else {
                    runOnUiThread {
                        binding.gpsStatus.setText(R.string.gps_status_permission_required)
                        binding.gpsStatus.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_gps_disabled, 0, 0, 0
                        )
                    }
                }
                
                hapticHandler?.postDelayed(this, GPS_UPDATE_INTERVAL)
            }
        }
        
        hapticHandler?.post(locationUpdateHandler)
    }

    private fun setupBatteryStatus() {
        val batteryUpdateHandler = object : Runnable {
            override fun run() {
                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                
                val batteryPercent = if (level >= 0 && scale >= 0) (level * 100 / scale) else -1
                
                val drawableRes = when {
                    batteryPercent >= 80 -> R.drawable.ic_battery_full
                    batteryPercent >= 30 -> R.drawable.ic_battery_medium
                    batteryPercent > 0 -> R.drawable.ic_battery_low
                    else -> R.drawable.ic_battery_unknown
                }
                
                runOnUiThread {
                    binding.batteryStatus.text = "${batteryPercent}%"
                    binding.batteryStatus.setCompoundDrawablesWithIntrinsicBounds(
                        drawableRes, 0, 0, 0
                    )
                    
                    if (batteryPercent < 20) {
                        binding.batteryStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.warning_yellow))
                    } else {
                        binding.batteryStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    }
                }
                
                hapticHandler?.postDelayed(this, BATTERY_UPDATE_INTERVAL)
            }
        }
        
        hapticHandler?.post(batteryUpdateHandler)
    }

    private fun setupNetworkStatus() {
        val networkUpdateHandler = object : Runnable {
            override fun run() {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                
                val network = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                
                val hasCellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
                val hasWiFi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
                
                val isOnline = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
                
                runOnUiThread {
                    if (isOnline) {
                        val text = StringBuilder()
                        text.append(if (hasCellular) " Cellular" else "")
                        text.append(if (hasWiFi) " WiFi" else "")
                        binding.networkStatus.text = text.toString().trim()
                        binding.networkStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.safe_green))
                        binding.networkStatus.setCompoundDrawablesWithIntrinsicBounds(
                            if (hasCellular) R.drawable.ic_cellular else R.drawable.ic_wifi, 0, 0, 0
                        )
                    } else {
                        binding.networkStatus.text = "No Connection"
                        binding.networkStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.warning_yellow))
                        binding.networkStatus.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_no_connection, 0, 0, 0
                        )
                    }
                }
                
                hapticHandler?.postDelayed(this, NETWORK_UPDATE_INTERVAL)
            }
        }
        
        hapticHandler?.post(networkUpdateHandler)
    }

    private fun localEmergencyNumber(): String {
        val iso = (getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager)
            ?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: java.util.Locale.getDefault().country
        return when (iso.uppercase()) {
            "KR" -> "119"
            "US", "CA" -> "911"
            "GB", "IE" -> "999"
            "JP" -> "119"
            "CN" -> "110"
            else -> "112"
        }
    }

    private fun setupQuickContacts() {
        val quickContacts = listOf(
            Pair("Mom", "+1234567890"),
            Pair("Dad", "+1234567891"),
            Pair("Emergency", localEmergencyNumber())
        )
        
        quickContacts.forEach { (name, phoneNumber) ->
            val contactButton = com.google.android.material.button.MaterialButton(this)
            contactButton.layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.contact_button_width),
                resources.getDimensionPixelSize(R.dimen.contact_button_height)
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.contact_button_margin)
            }
            contactButton.text = name
            contactButton.textSize = 14f
            contactButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            contactButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dark_surface_variant))
            contactButton.cornerRadius = 16
            
            contactButton.setOnClickListener {
                dialEmergencyContact(phoneNumber)
            }
            
            binding.quickContactsLayout.addView(contactButton)
        }
    }

    private fun setupEventTimeline() {
        adapter = EventAdapter()
        binding.eventRecyclerView.adapter = adapter
        
        binding.eventRecyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        
        binding.filterAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setFilter(EventFilter.ALL)
            }
        }
        
        binding.filterEmergency.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setFilter(EventFilter.EMERGENCY)
            }
        }
        
        binding.filterSafety.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setFilter(EventFilter.SAFETY)
            }
        }
    }

    private fun setupPrivacyToggle() {
        binding.privacyModeToggle.isChecked = false
        binding.privacyModeToggle.setOnCheckedChangeListener { _, isChecked ->
            handlePrivacyToggle(isChecked)
        }
    }

    private fun startStatusUpdates() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
        
        if (cameraId != null && !binding.privacyModeToggle.isChecked) {
            startCameraPreview(cameraId)
        }
        
        val audioRecorder = AudioRecorder()
        
        val audioUpdateHandler = object : Runnable {
            override fun run() {
                val level = audioRecorder.getLevel()
                viewModel.updateAudioLevel(level)
                mainHandler.postDelayed(this, AUDIO_UPDATE_INTERVAL)
            }
        }
        
        mainHandler.post(audioUpdateHandler)
    }

    private fun stopStatusUpdates() {
        // Stop updates when app is backgrounded
    }

    private fun handlePrivacyToggle(isEnabled: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
        
        if (cameraId != null) {
            if (isEnabled) {
                stopCameraPreview()
                hapticFeedback(HAPTIC_LEVEL_LIGHT)
            } else {
                startCameraPreview(cameraId)
                hapticFeedback(HAPTIC_LEVEL_LIGHT)
            }
        }
    }

    private fun hapticFeedback(level: Int) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (vibrator.hasVibrator()) {
            val effect = if (level == HAPTIC_LEVEL_STRONG) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            
            vibrator.vibrate(effect)
        }
    }

    private fun activateEmergencyMode() {
        hapticFeedback(HAPTIC_LEVEL_STRONG)
        
        val intent = android.content.Intent(this, EmergencyActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        
        // Start alarm sound
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
        
        // Send emergency notification (direct to Telegram if configured)
        sendEmergencyNotification()
        
        // Send alert directly to Telegram and start location sharing + lost-mode monitoring
        UserPrefs.setSharingActive(this, true)
        executor.execute {
            val location = getLastKnownLocation()
            runCatching {
                kotlinx.coroutines.runBlocking {
                    com.memoria.service.TelegramDirect.sendEmergency(
                        this@MainActivity,
                        level = UserPrefs.LEVEL_2ND,
                        type = "sos_activated",
                        confidence = 1.0f,
                        location = location
                    )
                }
            }
            // 이메일 전송은 보류(Telegram 중심).
            com.memoria.service.LostModeService.start(this@MainActivity)

            // 증거 수집: 전후 10초 음성 + 최근 1시간 경로 + 화면 캡처
            com.memoria.service.EvidenceService.start(this@MainActivity)
            val audioFile = com.memoria.service.EvidenceService.capture(this@MainActivity)
            val pathFile = com.memoria.service.EvidenceService.saveCurrentPath(this@MainActivity)
            val screenFile = if (com.memoria.util.UserPrefs.isScreenCaptureEnabled(this@MainActivity)) {
                com.memoria.service.ScreenCaptureService.captureLatest(this@MainActivity)
            } else {
                null
            }
            runCatching {
                kotlinx.coroutines.runBlocking {
                    audioFile?.let { file ->
                        com.memoria.service.TelegramDirect.sendEvidenceFile(
                            this@MainActivity, file, "🚨 [Memoria 2차] SOS 증거: 전후 10초 음성"
                        )
                    }
                    pathFile?.let { file ->
                        com.memoria.service.TelegramDirect.sendEvidenceFile(
                            this@MainActivity, file, "🚨 [Memoria 2차] SOS 증거: 최근 1시간 경로"
                        )
                    }
                    screenFile?.let { file ->
                        com.memoria.service.TelegramDirect.sendEvidenceFile(
                            this@MainActivity, file, "🚨 [Memoria 2차] SOS 증거: 화면 캡처"
                        )
                    }
                    // 증거 이메일 전송은 보류(Telegram 중심).
                }
            }
        }
        
        // Start camera capture
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
        if (cameraId != null) {
            startCameraPreview(cameraId)
        }
        
        // Start location sharing
        startLocationSharing()
    }

    private fun getLastKnownLocation(): android.location.Location? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }
    }

    private fun sendEmergencyNotification() {
        val sharedPreferences = getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        val contacts = sharedPreferences.getStringSet(PREF_EMERGENCY_CONTACTS, emptySet())
        
        contacts?.forEach { phoneNumber ->
            // In production, integrate with SMS API or cloud service
            Log.d("Memoria", "Sending emergency notification to $phoneNumber")
        }
    }

    private fun dialEmergencyContact(phoneNumber: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
        intent.data = android.net.Uri.parse("tel:$phoneNumber")
        startActivity(intent)
        
        hapticFeedback(HAPTIC_LEVEL_LIGHT)
    }

    private fun startLocationSharing() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        
        val locationListener = android.location.LocationListener {
            // In production, share location with emergency contacts via backend
            Log.d("Memoria", "Location: ${it.latitude}, ${it.longitude}")
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL,
                MIN_LOCATION_DISTANCE,
                locationListener
            )
        }
    }

    private fun startCameraPreview(cameraId: String) {
        try {
            // In production, integrate with Camera2 API for actual preview
            // For now, just log
            Log.d("Memoria", "Starting camera preview for $cameraId")
        } catch (e: Exception) {
            Log.e("Memoria", "Camera preview error: ${e.message}")
        }
    }

    private fun stopCameraPreview() {
        Log.d("Memoria", "Stopping camera preview")
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val LONG_PRESS_DURATION = 3000L
        private const val TAP_TIMEOUT = 500L
        private const val GPS_UPDATE_INTERVAL = 5000L
        private const val BATTERY_UPDATE_INTERVAL = 30000L
        private const val NETWORK_UPDATE_INTERVAL = 10000L
        private const val AUDIO_UPDATE_INTERVAL = 100L
        private const val LOCATION_UPDATE_INTERVAL = 5000L
        private const val MIN_LOCATION_DISTANCE = 10f
        private const val HAPTIC_LEVEL_LIGHT = 1
        private const val HAPTIC_LEVEL_STRONG = 2
        private const val PREFERENCES_FILE = "memoria_prefs"
        private const val PREF_EMERGENCY_CONTACTS = "emergency_contacts"
    }
}
