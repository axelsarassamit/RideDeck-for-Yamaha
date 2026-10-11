package com.axelsarassamit.gx12

import android.speech.tts.TextToSpeech
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.speech.tts.UtteranceProgressListener

/** Reject a device/default Thai voice rather than silently falling back to it. */
object NavigationVoice {
    const val SAMPLE = "In 150 meters, turn left. Navigation voice is English."
    fun configure(speaker: TextToSpeech): Boolean {
        if (speaker.setLanguage(NavigationSpeech.locale) < 0) return false
        val voices = speaker.voices.orEmpty().filter { NavigationSpeech.acceptsVoice(it.locale.language) }
        val voice = voices.sortedWith(compareBy({ it.isNetworkConnectionRequired },
            { it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) },
            { it.locale.country != "US" }, { -it.quality }, { it.name })).firstOrNull() ?: return false
        return speaker.setVoice(voice) == TextToSpeech.SUCCESS &&
            speaker.voice?.locale?.language?.let(NavigationSpeech::acceptsVoice) == true
    }
    fun sample(context: Context) {
        val main=Handler(Looper.getMainLooper())
        var engine: TextToSpeech? = null
        val close=Runnable { engine?.shutdown(); engine=null }
        engine=TextToSpeech(context.applicationContext) { result -> main.post {
            val voice=engine ?: return@post
            val ready=result == TextToSpeech.SUCCESS && configure(voice)
            BikeDiagnostics.record(context,"English voice test ready=$ready locale=${voice.voice?.locale?.toLanguageTag().orEmpty()}")
            if(!ready) {
                Toast.makeText(context,"English voice unavailable. Install English speech data in Android speech settings.",Toast.LENGTH_LONG).show()
                close.run()
            } else {
                voice.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                voice.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post(close) }
                    @Deprecated("Android legacy callback") override fun onError(id: String?) { main.post(close) }
                })
                if(voice.speak(SAMPLE,TextToSpeech.QUEUE_FLUSH,null,"english-test") != TextToSpeech.SUCCESS) close.run()
            }
        } }
        main.postDelayed(close,15000)
    }
}
