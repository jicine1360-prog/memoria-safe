package com.memoria.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.memoria.R
import com.memoria.databinding.ActivitySettingsBinding
import com.memoria.service.EmailDirect
import com.memoria.service.PatternService
import com.memoria.service.TelegramDirect
import com.memoria.util.PatternConfig
import com.memoria.util.PatternDetector
import com.memoria.util.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var tapDetected = false
    private var shakeDetected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTelegramSection()
        setupTapSection()
        setupShakeSection()
        setupMonitoringSwitch()
        setupEmailSection()
        setupScreenCaptureSection()
    }

    override fun onStart() {
        super.onStart()
        PatternService.testListener = testListener
    }

    override fun onStop() {
        super.onStop()
        PatternService.testListener = null
        PatternService.endTest()
    }

    override fun onDestroy() {
        scope.cancel()
        PatternService.testListener = null
        super.onDestroy()
    }

    // ------------------------------------------------------------- telegram

    private fun setupTelegramSection() {
        binding.tgTokenInput.setText(UserPrefs.getTelegramBotToken(this))
        binding.tgChatInput.setText(UserPrefs.getTelegramChatIds(this).joinToString(", "))

        binding.tgTestButton.setOnClickListener {
            val count = saveTelegramSettings()
            if (count == 0) return@setOnClickListener

            binding.tgTestResult.text = getString(R.string.settings_tg_test_result) +
                " ($count 명에게) 전송 중…"
            scope.launch {
                val ok = TelegramDirect.sendTest(this@SettingsActivity)
                binding.tgTestResult.text = getString(R.string.settings_tg_test_result) +
                    if (ok) " 성공 ✅" else " 실패 ❌ (토큰/챗ID 확인)"
            }
        }
    }

    private fun saveTelegramSettings(): Int {
        val token = binding.tgTokenInput.text?.toString()?.trim().orEmpty()
        val rawChats = binding.tgChatInput.text?.toString()?.trim().orEmpty()
        val chats = rawChats
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (token.isEmpty() || chats.isEmpty()) {
            binding.tgTestResult.text = "봇 토큰과 받는 사람 챗ID를 모두 입력하세요."
            return 0
        }
        UserPrefs.setTelegramBotToken(this, token)
        UserPrefs.setTelegramChatIds(this, chats)
        return chats.size
    }

    // ------------------------------------------------------------------ tap

    private fun setupTapSection() {
        val cfg = UserPrefs.getTapPattern(this)
        binding.tapCountSlider.value = cfg.count.toFloat()
        binding.tapCountValue.text = getString(R.string.settings_tap_count_label) + ": ${cfg.count}회"
        if (cfg.level == UserPrefs.LEVEL_2ND) binding.tapLevel2nd.isChecked = true

        binding.tapCountSlider.addOnChangeListener { _, value, _ ->
            binding.tapCountValue.text =
                getString(R.string.settings_tap_count_label) + ": ${value.toInt()}회"
        }

        binding.tapTestButton.setOnClickListener {
            tapDetected = false
            binding.tapSaveButton.isEnabled = false
            binding.tapStatus.text = "실험 시작 — 폰을 ${binding.tapCountSlider.value.toInt()}회 톡톡 치세요"
            PatternService.testListener = testListener
            PatternService.beginTest(PatternDetector.PatternKind.TAP, binding.tapCountSlider.value.toInt())
        }

        binding.tapSaveButton.setOnClickListener {
            saveTapPattern()
        }
    }

    private fun saveTapPattern() {
        val count = binding.tapCountSlider.value.toInt()
        val level = if (binding.tapLevel2nd.isChecked) UserPrefs.LEVEL_2ND else UserPrefs.LEVEL_1ST
        UserPrefs.setTapPattern(this, PatternConfig(count, level))
        PatternService.endTest()
        binding.tapStatus.text = "저장됨: 톡톡 ${count}회 (${levelText(level)})"
        binding.tapSaveButton.isEnabled = false
    }

    // ---------------------------------------------------------------- shake

    private fun setupShakeSection() {
        val cfg = UserPrefs.getShakePattern(this)
        binding.shakeCountSlider.value = cfg.count.toFloat()
        binding.shakeCountValue.text = getString(R.string.settings_shake_count_label) + ": ${cfg.count}회"
        if (cfg.level == UserPrefs.LEVEL_2ND) binding.shakeLevel2nd.isChecked = true

        binding.shakeCountSlider.addOnChangeListener { _, value, _ ->
            binding.shakeCountValue.text =
                getString(R.string.settings_shake_count_label) + ": ${value.toInt()}회"
        }

        binding.shakeTestButton.setOnClickListener {
            shakeDetected = false
            binding.shakeSaveButton.isEnabled = false
            binding.shakeStatus.text = "실험 시작 — 폰을 ${binding.shakeCountSlider.value.toInt()}회 흔드세요"
            PatternService.testListener = testListener
            PatternService.beginTest(PatternDetector.PatternKind.SHAKE, binding.shakeCountSlider.value.toInt())
        }

        binding.shakeSaveButton.setOnClickListener {
            saveShakePattern()
        }
    }

    private fun saveShakePattern() {
        val count = binding.shakeCountSlider.value.toInt()
        val level = if (binding.shakeLevel2nd.isChecked) UserPrefs.LEVEL_2ND else UserPrefs.LEVEL_1ST
        UserPrefs.setShakePattern(this, PatternConfig(count, level))
        PatternService.endTest()
        binding.shakeStatus.text = "저장됨: 흔들기 ${count}회 (${levelText(level)})"
        binding.shakeSaveButton.isEnabled = false
    }

    private fun levelText(level: Int): String =
        if (level == UserPrefs.LEVEL_2ND) "2차 긴급" else "1차 경계"

    // ---------------------------------------------------------------- email

    private fun setupEmailSection() {
        val cfg = UserPrefs.getEmailConfig(this)
        binding.emailHostInput.setText(cfg.host)
        binding.emailPortInput.setText(cfg.port.toString())
        binding.emailUserInput.setText(cfg.user)
        binding.emailPassInput.setText(cfg.password)
        binding.emailFromInput.setText(cfg.from)
        binding.emailToInput.setText(cfg.recipient)

        binding.emailTestButton.setOnClickListener {
            if (!saveEmailSettings()) return@setOnClickListener

            binding.emailTestResult.text = getString(R.string.settings_email_test_result) + " 전송 중…"
            scope.launch {
                val ok = EmailDirect.sendTest(this@SettingsActivity)
                binding.emailTestResult.text = getString(R.string.settings_email_test_result) +
                    if (ok) " 성공 ✅" else " 실패 ❌ (SMTP 설정 확인)"
            }
        }
    }

    private fun saveEmailSettings(): Boolean {
        val host = binding.emailHostInput.text?.toString()?.trim().orEmpty()
        val port = binding.emailPortInput.text?.toString()?.trim()?.toIntOrNull() ?: 587
        val user = binding.emailUserInput.text?.toString()?.trim().orEmpty()
        val pass = binding.emailPassInput.text?.toString()?.trim().orEmpty()
        val from = binding.emailFromInput.text?.toString()?.trim().orEmpty()
        val to = binding.emailToInput.text?.toString()?.trim().orEmpty()

        if (host.isEmpty() || user.isEmpty() || from.isEmpty() || to.isEmpty()) {
            binding.emailTestResult.text = "호스트/계정/보내는사람/받는사람을 모두 입력하세요."
            return false
        }

        UserPrefs.setEmailConfig(
            this,
            UserPrefs.EmailConfig(host, port, user, pass, from, to)
        )
        return true
    }

    // ------------------------------------------------------------ monitoring

    private fun setupMonitoringSwitch() {
        binding.monitoringSwitch.isChecked = PatternService.isMonitoring()
        binding.monitoringSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (UserPrefs.isTelegramConfigured(this)) {
                    PatternService.start(this)
                    binding.monitoringSwitch.isChecked = PatternService.isMonitoring()
                } else {
                    binding.monitoringSwitch.isChecked = false
                }
            } else {
                PatternService.stop(this)
            }
        }
    }

    // ------------------------------------------------------- screen capture

    private companion object {
        const val SCREEN_CAPTURE_REQUEST = 6001
    }

    private fun setupScreenCaptureSection() {
        val enabled = UserPrefs.isScreenCaptureEnabled(this)
        binding.screenCaptureSwitch.isChecked = enabled

        binding.screenCaptureSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestScreenCapturePermission()
            } else {
                UserPrefs.setScreenCaptureEnabled(this, false)
                com.memoria.service.ScreenCaptureService.stopCapture(this)
            }
        }
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
            as android.media.projection.MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                UserPrefs.setScreenCaptureEnabled(this, true)
                binding.screenCaptureSwitch.isChecked = true
                com.memoria.service.ScreenCaptureService.startCapture(this, resultCode, data)
            } else {
                UserPrefs.setScreenCaptureEnabled(this, false)
                binding.screenCaptureSwitch.isChecked = false
            }
        }
    }

    // ------------------------------------------------------------- listener

    private val testListener = object : PatternService.TestListener {
        override fun onProgress(kind: PatternDetector.PatternKind, current: Int, target: Int) {
            runOnUiThread {
                when (kind) {
                    PatternDetector.PatternKind.TAP ->
                        binding.tapStatus.text = "감지 진행: $current / $target 회"
                    PatternDetector.PatternKind.SHAKE ->
                        binding.shakeStatus.text = "감지 진행: $current / $target 회"
                }
            }
        }

        override fun onTestDetected(kind: PatternDetector.PatternKind) {
            runOnUiThread {
                when (kind) {
                    PatternDetector.PatternKind.TAP -> {
                        tapDetected = true
                        binding.tapStatus.text = "✅ 패턴 감지 완료! '통과 → 저장'을 누르면 설정됩니다."
                        binding.tapSaveButton.isEnabled = true
                    }
                    PatternDetector.PatternKind.SHAKE -> {
                        shakeDetected = true
                        binding.shakeStatus.text = "✅ 패턴 감지 완료! '통과 → 저장'을 누르면 설정됩니다."
                        binding.shakeSaveButton.isEnabled = true
                    }
                }
            }
        }

        override fun onCalibrationLevel(level: Int) = Unit
    }
}