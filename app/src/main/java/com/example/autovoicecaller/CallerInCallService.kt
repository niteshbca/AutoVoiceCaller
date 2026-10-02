package com.example.autovoicecaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService

class CallerInCallService : InCallService() {
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    private var endpoints = emptyList<CallEndpoint>()
    override fun onCreate() {
        super.onCreate()
        Session.service = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("calls", "Phone calls", NotificationManager.IMPORTANCE_HIGH))
    }
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Session.onAdded(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Session.onState(call, state); notifyCalls()
            }
            override fun onDetailsChanged(call: Call, details: Call.Details) { Session.refresh(); notifyCalls() }
        }
        callbacks[call] = callback; call.registerCallback(callback)
        Session.onState(call, call.state)
        notifyCalls()
        // Telecom explicitly permits the dialer's in-call UI; no overlay/accessibility workaround.
        try { startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        catch (_: Exception) { Session.show("Open Auto Voice Caller from call notification") }
    }
    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let(call::unregisterCallback)
        Session.onRemoved(call); notifyCalls(); super.onCallRemoved(call)
    }
    @Deprecated("Legacy call audio callback")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (Build.VERSION.SDK_INT < 34 && audioState != null) {
            Session.setAudioStatus(if (audioState.route == CallAudioState.ROUTE_SPEAKER) "Call speaker ON; TTS media path must be tested" else "Call speaker OFF / unavailable")
        }
    }
    @android.annotation.TargetApi(34)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        endpoints = availableEndpoints.toList()
    }
    @android.annotation.TargetApi(34)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        Session.setAudioStatus(if (callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER) "Call speaker ON; TTS media path must be tested" else "Call audio routed to ${callEndpoint.endpointName}")
    }
    fun requestSpeaker() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val speaker = endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
                if (speaker == null) { Session.setAudioStatus("Speaker endpoint unavailable: use Speaker button / test device"); return }
                requestCallEndpointChange(speaker, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) { Session.setAudioStatus("Speaker request accepted; acoustic transmission not guaranteed") }
                    override fun onError(error: CallEndpointException) { Session.setAudioStatus("Speaker routing failed: ${error.message}") }
                })
            } else {
                @Suppress("DEPRECATION")
                setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        } catch (e: Exception) { Session.setAudioStatus("Speaker/audio routing unavailable: ${e.message}") }
    }
    fun notifyCalls() {
        val manager = getSystemService(NotificationManager::class.java)
        val call = Session.calls.firstOrNull { it.state == Call.STATE_RINGING } ?: Session.calls.firstOrNull()
        if (call == null) { manager.cancel(1); return }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ringing = call.state == Call.STATE_RINGING
        val notification = Notification.Builder(this, "calls")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(if (ringing) "Incoming SIM call" else "SIM call in progress")
            .setContentText("Tap to open call controls")
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setContentIntent(open)
            .setOnlyAlertOnce(true)
        if (ringing) notification.addAction(Notification.Action.Builder(null, "Answer", action("ANSWER", 1)).build())
        notification.addAction(Notification.Action.Builder(null, if (ringing) "Reject" else "End", action("END", 2)).build())
        if (Session.running) notification.addAction(Notification.Action.Builder(null, "Stop queue", action("STOP", 3)).build())
        try { manager.notify(1, notification.build()) } catch (_: SecurityException) { Session.show("Notifications denied: keep call controls open") }
    }
    private fun action(action: String, request: Int) = PendingIntent.getBroadcast(this, request,
        Intent(this, CallActionReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun onDestroy() {
        callbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }; callbacks.clear()
        if (Session.running && Session.calls.isNotEmpty()) Session.stop("Phone service lost during call; queue cancelled")
        Session.calls.clear(); Session.service = null
        getSystemService(NotificationManager::class.java).cancel(1)
        super.onDestroy()
    }
}
