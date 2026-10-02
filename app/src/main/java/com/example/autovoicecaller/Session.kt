package com.example.autovoicecaller

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.lifecycle.MutableLiveData

/** In-memory user-started session; never resumed after process death. All mutations on main. */
object Session {
    lateinit var app: Application
        private set
    lateinit var speech: SpeechEngine
        private set
    val updates = MutableLiveData(0)
    private val handler = Handler(Looper.getMainLooper())
    private var revision = 0
    var status = "Ready"
        private set
    var audioStatus = "Speaker routing not requested"
        private set
    var running = false
        private set
    var visible = false
    var stateAccess = false
    var awaitingNext = false
        private set
    var manual = false
        private set
    var phoneState = TelephonyManager.CALL_STATE_IDLE
        private set
    var service: CallerInCallService? = null
    val calls = mutableListOf<Call>()
    var ownedCall: Call? = null
        private set
    private var waitingForCall = false
    private var manualCallStarted = false
    private var spoken = false
    private var automaticEnd = false
    private var queue = emptyList<String>()
    private var message = ""
    private var delay = 2000L
    private var index = -1
    private var generation = 0
    private var watchdog: Runnable? = null
    val log = mutableListOf<String>()
    var useServer = false
        private set
    private lateinit var serverCaller: ServerCaller

    fun initialize(application: Application) { app = application; speech = SpeechEngine(app); serverCaller = ServerCaller(app) }
    
    fun setServerMode(enabled: Boolean) { useServer = enabled }
    fun refresh() { updates.value = ++revision }
    fun show(text: String) { status = text; refresh() }
    fun isDialer(): Boolean = app.getSystemService(TelecomManager::class.java).defaultDialerPackage == app.packageName
    fun audioCapabilities(): String = "AEC available: ${AcousticEchoCanceler.isAvailable()}; NS available: ${NoiseSuppressor.isAvailable()}. Cellular DSP is managed by Android; these effects are not attached by this app."
    fun start(numbers: List<String>, text: String, delaySeconds: Int, maximum: Int) {
        if (running) return
        if (numbers.isEmpty() || numbers.size > maximum || numbers.any { NumberRules.normalize(it) == null }) { show("Invalid phone number or session limit exceeded"); return }
        if (text.isBlank() || text.length > android.speech.tts.TextToSpeech.getMaxSpeechInputLength()) { show("Message must contain 1–4000 characters"); return }
        if (!speech.isReady()) { show(speech.description); return }
        if (app.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) { show("Call permission denied"); return }
        if (!stateAccess) { show("Call-state access unavailable: grant permission and reopen app"); return }
        if (calls.isNotEmpty() || phoneState != TelephonyManager.CALL_STATE_IDLE) { show("Finish existing calls before starting"); return }
        queue = numbers.toList(); message = text; delay = delaySeconds * 1000L
        manual = !isDialer(); index = -1; generation++; log.clear(); running = true
        next()
    }
fun next() {
        if (!running) return
        if (useServer) {
            nextServer()
            return
        }
        if (!manual && !isDialer()) { stop("Phone role removed: queue stopped"); return }
        if (calls.isNotEmpty() || phoneState != TelephonyManager.CALL_STATE_IDLE) { awaitingNext = true; show("Waiting for phone IDLE; tap Continue when idle"); return }
        if (!visible) { awaitingNext = true; show("Queue paused: reopen app and tap Continue"); return }
        awaitingNext = false
        if (++index >= queue.size) { running = false; show("Completed"); return }
        spoken = false; automaticEnd = false; ownedCall = null; manualCallStarted = false
        waitingForCall = true; show("Calling: ${queue[index]}")
        try {
            val telecom = app.getSystemService(TelecomManager::class.java)
            val uri = Uri.fromParts("tel", queue[index], null)
            val emergency = if (android.os.Build.VERSION.SDK_INT >= 29) app.getSystemService(TelephonyManager::class.java).isEmergencyNumber(queue[index]) else android.telephony.PhoneNumberUtils.isEmergencyNumber(queue[index])
            if (emergency) throw IllegalArgumentException("Emergency numbers cannot be queued")
            if (manual) app.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else telecom.placeCall(uri, android.os.Bundle())
            val ticket = generation
            watchdog = Runnable {
                if (running && ticket == generation && !spoken) {
                    stop("Call failed or no answer (90-second timeout)")
                }
            }.also { handler.postDelayed(it, 90_000) }
        } catch (_: SecurityException) { stop("Call permission denied")
        } catch (e: Exception) { stop("Phone call cannot be started: ${e.message ?: e.javaClass.simpleName}") }
    }
    
    private fun nextServer() {
        if (!running) return
        if (++index >= queue.size) { running = false; show("Completed"); return }
        spoken = false; automaticEnd = false; ownedCall = null; manualCallStarted = false
        waitingForCall = true; show("Calling via server: ${queue[index]}")
        
        serverCaller.makeCall(queue[index], message, object : ServerCaller.CallCallback {
            override fun onSuccess(callId: String, channelId: String) {
                handler.post {
                    if (!running) return@post
                    show("Server call initiated: $callId")
                    val ticket = generation
                    watchdog = Runnable {
                        if (running && ticket == generation && !spoken) {
                            stop("Server call timeout")
                        }
                    }.also { handler.postDelayed(it, 120_000) }
                }
            }
            override fun onError(error: String) {
                handler.post {
                    if (!running) return@post
                    stop("Server call failed: $error")
                }
            }
        })
    }
            }.also { handler.postDelayed(it, 90_000) }
        } catch (_: SecurityException) { stop("Call permission denied")
        } catch (e: Exception) { stop("Phone call cannot be started: ${e.message ?: e.javaClass.simpleName}") }
    }
    fun onAdded(call: Call) {
        calls.add(call)
        val outgoing = if (android.os.Build.VERSION.SDK_INT >= 29) call.details.callDirection == Call.Details.DIRECTION_OUTGOING else call.state != Call.STATE_RINGING
        val number = call.details.handle?.schemeSpecificPart
        if (running && !manual && waitingForCall && outgoing && number != null &&
            android.telephony.PhoneNumberUtils.compare(number, queue[index])) {
            ownedCall = call; waitingForCall = false
        } else if (running) stop("Queue stopped: another/incoming call detected")
        refresh()
    }
    fun onState(call: Call, state: Int) {
        if (call != ownedCall || !running) { refresh(); return }
        when (state) {
            Call.STATE_ACTIVE -> if (!spoken) { show("Call connected"); speakNow() }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> show("Calling: ${queue[index]} (waiting for answer)")
            Call.STATE_HOLDING -> stop("Queue stopped: call placed on hold")
            Call.STATE_DISCONNECTED -> {
                speech.stop(); cancelWatchdog()
                val cause = call.details.disconnectCause
                log.add("${queue[index]}: ${cause.label ?: "Call ended"} (code ${cause.code})")
                // Advance only after our requested post-TTS disconnect. Manual early hang-up stops the queue.
                if (!automaticEnd) stop("Call ended: ${cause.label ?: "rejected / busy / network / user ended"}")
                else show("Call ended")
            }
        }
    }
    fun onRemoved(call: Call) {
        calls.remove(call)
        if (call == ownedCall) {
            ownedCall = null
            if (running && automaticEnd) {
                show("Calling next number...")
                val ticket = generation
                handler.postDelayed({ if (running && ticket == generation) next() }, 1200)
            } else if (running) stop("Call ended")
        }
        refresh()
    }
    fun onPhoneState(state: Int) {
        val previous = phoneState; phoneState = state
        if (running && manual) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> stop("Queue stopped: incoming call")
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    manualCallStarted = true; waitingForCall = false
                    if (!spoken) show("OFFHOOK: may still be dialing. After customer answers, return and tap Customer answered")
                }
                TelephonyManager.CALL_STATE_IDLE -> if (manualCallStarted && previous != state) {
                    speech.stop(); cancelWatchdog()
                    if (spoken && automaticEnd) {
                        log.add("${queue[index]}: manually ended after message")
                        awaitingNext = true; show("Call ended. Tap Continue for next number")
                    } else stop("Call ended before message completed")
                }
            }
        }
        if (running && !manual && awaitingNext && visible && state == TelephonyManager.CALL_STATE_IDLE && calls.isEmpty()) {
            val ticket = generation
            handler.postDelayed({ if (ticket == generation) continueQueue() }, 300)
        }
        refresh()
    }
    fun confirmAnswered() {
        if (running && manual && manualCallStarted && phoneState == TelephonyManager.CALL_STATE_OFFHOOK && !spoken) {
            show("Call connected (user confirmed)"); speakNow()
        }
    }
    private fun speakNow() {
        if (!running || spoken) return
        spoken = true; cancelWatchdog()
        val ticket = generation
        audioStatus = "Requesting speaker; actual TTS media routing remains device-dependent"
        service?.requestSpeaker()
        if (manual) {
            try {
                @Suppress("DEPRECATION")
                val manager = app.getSystemService(AudioManager::class.java)
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = true
                audioStatus = "Manual mode: speaker request best effort; enable Speaker in system dialer"
            } catch (_: Exception) { audioStatus = "Speaker/audio routing unavailable; enable Speaker manually" }
        }
        show("Speaking message...")
        // Routing needs a moment; verify the call is still active before starting TTS.
        handler.postDelayed({
            if (!running || ticket != generation) return@postDelayed
            if ((!manual && ownedCall?.state != Call.STATE_ACTIVE) || (manual && phoneState != TelephonyManager.CALL_STATE_OFFHOOK)) {
                stop("Call ended before speech"); return@postDelayed
            }
            speech.speak(message, {
                if (running && ticket == generation) {
                    show("Message completed")
                    handler.postDelayed({
                        if (running && ticket == generation) {
                            automaticEnd = true
                            if (manual) show("Message completed: end call in system dialer, then tap Continue")
                            else try {
                                ownedCall?.disconnect()
                                handler.postDelayed({
                                    if (running && ticket == generation && ownedCall != null) {
                                        show("Automatic ending unavailable: end this call manually")
                                    }
                                }, 5000)
                            } catch (_: Exception) { show("Automatic ending unavailable: end this call manually") }
                        }
                    }, delay)
                }
            }, { error -> stop(error) })
        }, 700)
        // A stalled engine cannot keep a call open indefinitely.
        watchdog = Runnable { if (running && ticket == generation) stop("TTS timeout: session stopped") }
            .also { handler.postDelayed(it, 180_000) }
    }
    fun setAudioStatus(text: String) { audioStatus = text; refresh() }
    private fun cancelWatchdog() { watchdog?.let(handler::removeCallbacks); watchdog = null }
    fun stop(reason: String = "Stopped") {
        generation++; running = false; awaitingNext = false; waitingForCall = false
        speech.stop(); cancelWatchdog(); show(reason)
        try { ownedCall?.takeIf { it.state != Call.STATE_DISCONNECTED }?.disconnect() }
        catch (_: Exception) { show("$reason. End current call manually") }
        if (manual && phoneState != TelephonyManager.CALL_STATE_IDLE) show("$reason. End current call in system dialer")
    }
    fun continueQueue() {
        if (running && awaitingNext && visible) { awaitingNext = false; next() }
    }
}
