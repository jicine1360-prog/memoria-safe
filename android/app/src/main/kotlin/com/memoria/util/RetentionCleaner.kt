package com.memoria.util

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 기기에 저장된 증거 파일(음성·경로·화면 캡처)을 보존기간 경과 후 자동 삭제한다.
 * 전송 성공 여부와 무관하게 오래된 로컬 사본을 정리해 개인정보 보존을 최소화한다.
 */
object RetentionCleaner {
    private const val TAG = "MemoriaRetention"

    /** 로컬 증거 보존기간(일). 개인정보처리방침에 고지된 값과 일치해야 한다. */
    const val RETENTION_DAYS = 7L

    fun clean(context: Context) {
        val dir = File(context.filesDir, "evidence")
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff && file.delete()) {
                removed++
            }
        }
        if (removed > 0) {
            Log.i(TAG, "오래된 증거 파일 $removed 건 삭제 (보존 ${RETENTION_DAYS}일)")
        }
    }
}
