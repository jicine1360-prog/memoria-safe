package com.memoria.service

import android.util.Log
import com.memoria.model.EmergencyEvent

object EmergencyResponder {

    private const val TAG = "Memoria"

    fun triggerEmergency(event: EmergencyEvent) {
        Log.w(
            TAG,
            "EMERGENCY TRIGGERED type=${event.type} confidence=${event.confidence} " +
                "lat=${event.location.latitude} lon=${event.location.longitude}"
        )
        event.save()
    }
}