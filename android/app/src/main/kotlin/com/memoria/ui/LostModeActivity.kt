package com.memoria.ui

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.memoria.R

/**
 * 분실/보호 모드 표시 화면.
 * 사용자가 설정한 보호자가 원격으로 분실 모드를 켜면 표시된다.
 * 기존 "전원 꺼진 척"(위장 종료) 화면과 달리, 이 화면은 분실 사실과
 * 위치 공유가 진행 중임을 기기 사용자에게 투명하게 안내한다.
 */
class LostModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = TextView(this).apply {
            text = getString(R.string.lost_mode_title)
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val body = TextView(this).apply {
            text = getString(R.string.lost_mode_body)
            textSize = 15f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1.1f)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 0, 48, 0)
            addView(title)
            addView(
                body,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 28 }
            )
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(content)
        }
        setContentView(root)
    }

    override fun onBackPressed() {
        // 분실 모드가 해제될 때까지 화면 유지
    }
}