package com.example.walkrun.tracking

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

/** Text-to-speech in Hebrew. If no Hebrew voice is installed, the device default language is used. */
class VoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val pending = mutableListOf<String>()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.forLanguageTag("he-IL"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.getDefault()
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        pending.forEach { speak(it) }
        pending.clear()
    }

    fun speak(text: String) {
        if (!ready) {
            pending.add(text)
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
