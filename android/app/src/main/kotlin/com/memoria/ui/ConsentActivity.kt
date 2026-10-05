package com.memoria.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.memoria.R
import com.memoria.databinding.ActivityConsentBinding
import com.memoria.util.UserPrefs

/**
 * 첫 실행 동의 화면.
 * 감지(톡톡/흔들기/SOS)·위치 공유·증거 전송(음성/화면)이
 * 사용자가 설정한 수신자에게 전송됨을 명확히 안내하고,
 * 명시적 동의를 받은 뒤에만 앱을 사용할 수 있게 한다.
 */
class ConsentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.consentAgreeButton.setOnClickListener {
            UserPrefs.setConsented(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.consentDeclineButton.setOnClickListener {
            finishAffinity()
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}