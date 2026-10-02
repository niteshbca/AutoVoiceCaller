package com.example.autovoicecaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.VideoProfile

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val call = Session.calls.firstOrNull { it.state == Call.STATE_RINGING } ?: Session.calls.firstOrNull()
            when (intent.action) {
                "ANSWER" -> call?.takeIf { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
                "END" -> { if (Session.running) Session.stop("Stopped by user"); call?.disconnect() }
                "STOP" -> Session.stop()
            }
        } catch (_: Exception) { Session.show("Call control unavailable: use device Phone app") }
    }
}
