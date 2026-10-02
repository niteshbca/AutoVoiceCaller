package com.example.autovoicecaller

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SpeechEngine(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var token = 0L
    private var done: (() -> Unit)? = null
    private var failed: ((String) -> Unit)? = null
    private lateinit var engine: TextToSpeech
    var description = "TTS initializing..."
        private set

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val language = engine.setLanguage(Locale("hi", "IN"))
                ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
                description = if (ready) "Hindi TTS ready" else "Hindi TTS unavailable: install Hindi voice data in Android settings"
                engine.setSpeechRate(0.9f)
                engine.setPitch(1.0f)
                // Media speaker playback: never a cellular uplink injection API.
                engine.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            } else description = "TTS unavailable: install/enable a TTS engine"
            Session.refresh()
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { handler.post {
                if (utteranceId == token.toString()) { val callback = done; done = null; failed = null; callback?.invoke() }
            } }
            @Deprecated("Legacy TTS error callback")
            override fun onError(utteranceId: String?) { reportError(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { reportError(utteranceId) }
        })
    }
    private fun reportError(id: String?) { handler.post {
        if (id == token.toString()) { val callback = failed; done = null; failed = null; callback?.invoke("TTS playback failed") }
    } }
    fun isReady() = ready
    fun speak(message: String, complete: () -> Unit, error: (String) -> Unit) {
        stop()
        if (!ready) { error(description); return }
        done = complete; failed = error
        val result = engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, token.toString())
        if (result == TextToSpeech.ERROR) { done = null; failed = null; error("TTS could not start") }
    }
    fun stop() { token++; done = null; failed = null; engine.stop() }
}
