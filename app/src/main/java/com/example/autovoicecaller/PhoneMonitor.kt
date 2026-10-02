package com.example.autovoicecaller

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/** Coarse state only. OFFHOOK is never treated as proof of an answered call. */
class PhoneMonitor(private val context: Context) {
    private val manager = context.getSystemService(TelephonyManager::class.java)
    private var modern: TelephonyCallback? = null
    private var legacy: PhoneStateListener? = null
    fun start() {
        if (modern != null || legacy != null) return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) { Session.onPhoneState(state) }
                }
                modern = callback; manager.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val callback = object : PhoneStateListener() {
                    @Deprecated("Legacy phone state")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) { Session.onPhoneState(state) }
                }
                legacy = callback
                @Suppress("DEPRECATION")
                manager.listen(callback, PhoneStateListener.LISTEN_CALL_STATE)
            }
        Session.stateAccess = true
        } catch (_: Exception) { Session.stateAccess = false; modern = null; legacy = null; Session.show("Call-state access unavailable; automatic queue disabled") }
    }
    fun stop() {
        if (Build.VERSION.SDK_INT >= 31) modern?.let { manager.unregisterTelephonyCallback(it) }
        @Suppress("DEPRECATION")
        legacy?.let { manager.listen(it, PhoneStateListener.LISTEN_NONE) }
        modern = null; legacy = null
    }
}
