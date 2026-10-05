package com.memoria.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.AudioAttributes
import android.media.ImageReader
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.memoria.R
import com.memoria.data.repository.LocationRepository
import com.memoria.databinding.FragmentEmergencyBinding
import java.util.concurrent.Executors

class EmergencyActivity : AppCompatActivity() {

    private lateinit var binding: FragmentEmergencyBinding
    private var soundPool: SoundPool? = null
    private var alarmSoundId: Int = 0
    private var alarmPlaying = false
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    @Volatile private var flashingRed = true
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var locationRepository: LocationRepository? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = FragmentEmergencyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        window.statusBarColor = Color.RED
        window.navigationBarColor = Color.DKGRAY
        
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        
        setupAlarm()
        setupCamera()
        setupLocationSharing()
        setupControls()
        
        // Trigger haptic feedback
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        }
    }

    override fun onResume() {
        super.onResume()
        playAlarm()
    }

    override fun onPause() {
        super.onPause()
        stopAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        stopCamera()
    }

    private fun setupAlarm() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
        
        alarmSoundId = soundPool?.load(this, R.raw.alarm_sound, 1) ?: 0
    }

    private fun playAlarm() {
        if (alarmSoundId != 0 && !alarmPlaying) {
            soundPool?.play(alarmSoundId, 1f, 1f, 0, -1, 1f)
            alarmPlaying = true
            
            // Start flashing effect
            startFlashing()
        }
    }

    private fun stopAlarm() {
        soundPool?.stop(alarmSoundId)
        alarmPlaying = false
        stopFlashing()
    }

    private fun startFlashing() {
        mainHandler.post(object : Runnable {
            override fun run() {
                flashingRed = !flashingRed
                window.decorView.setBackgroundColor(if (flashingRed) Color.RED else Color.DKGRAY)
                mainHandler.postDelayed(this, 500)
            }
        })
    }

    private fun stopFlashing() {
        mainHandler.removeCallbacksAndMessages(null)
        window.decorView.setBackgroundColor(Color.RED)
    }

    private fun setupCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            
            val outputSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(Surface::class.java)
                ?: emptyArray()
            
            val previewSize = outputSizes.maxByOrNull { it.width * it.height }
            
            val textureView = binding.cameraPreview
            
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val displayRotation = windowManager.defaultDisplay.rotation

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    openCamera(cameraId, surfaceTexture)
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    stopCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraId: String, surfaceTexture: SurfaceTexture) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        val texture = surfaceTexture
        texture.setDefaultBufferSize(1920, 1080)
        val surface = Surface(texture)
        
        val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        
        cameraManager.openCamera(cameraId, executor, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startCaptureSession(camera, surface, imageReader)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
            }
        })
    }

    private fun startCaptureSession(camera: CameraDevice, surface: Surface, imageReader: ImageReader) {
        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)
        
        camera.createCaptureSession(listOf(surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                
                val tracker = captureRequestBuilder.build()
                session.setRepeatingRequest(tracker, null, mainHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, mainHandler)
    }

    private fun stopCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun setupLocationSharing() {
        locationRepository = LocationRepository(this)
        
        val updateLocationHandler = object : Runnable {
            override fun run() {
                if (ContextCompat.checkSelfPermission(this@EmergencyActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationRepository?.getLastLocation()?.let { location ->
                        binding.locationStatus.text = "Location: ${location.latitude}, ${location.longitude}"
                        
                        // In production, send to emergency contacts
                    }
                }
                
                mainHandler.postDelayed(this, 5000)
            }
        }
        
        mainHandler.post(updateLocationHandler)
    }

    private fun setupControls() {
        binding.cancelEmergencyButton.setOnClickListener {
            stopEmergency()
        }
        
        binding.lockEmergencyButton.setOnClickListener {
            hapticFeedback()
            finish()
        }
    }

    private fun stopEmergency() {
        stopAlarm()
        stopCamera()
        
        // Stop sharing with guardians and stop monitoring service
        com.memoria.util.UserPrefs.setSharingActive(this, false)
        executor.execute {
            kotlinx.coroutines.runBlocking {
                com.memoria.service.TelegramReporter.stopSharing(this@EmergencyActivity)
            }
        }
        com.memoria.service.LostModeService.stop(this)
        
        // Haptic confirmation
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        
        finish()
    }

    private fun hapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
