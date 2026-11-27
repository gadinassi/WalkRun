package com.example.walkrun.tracking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Text-to-speech in Hebrew. If no Hebrew voice is installed, the device default language is used.
 * While it speaks, music from other apps is turned down (audio focus "may duck"), and turned up again
 * when the last queued sentence ends.
 */
class VoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val pending = mutableListOf<String>()

    /** Turn the music down while speaking. Set from the settings at the start of each workout. */
    var duckMusic = true

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()
    private var hasFocus = false
    /** Sentences queued and not finished yet. Main thread only. */
    private var speaking = 0
    private val release = Runnable { if (speaking == 0) abandonFocus() }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.forLanguageTag("he-IL"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.getDefault()
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
        })
        ready = true
        pending.forEach { speak(it) }
        pending.clear()
    }

    fun speak(text: String) {
        if (!ready) {
            pending.add(text)
            return
        }
        main.removeCallbacks(release)
        if (duckMusic && !hasFocus) {
            hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        speaking++
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    /** Called on a TTS thread. A short delay keeps the music down between two close sentences. */
    private fun finished() {
        main.post {
            speaking = (speaking - 1).coerceAtLeast(0)
            if (speaking == 0) main.postDelayed(release, 400)
        }
    }

    private fun abandonFocus() {
        if (hasFocus) audio.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    fun shutdown() {
        main.removeCallbacks(release)
        abandonFocus()
        tts.stop()
        tts.shutdown()
    }
}
