package com.example

import android.app.Application
import com.example.audio.AudioExporter
import com.example.audio.PlaybackEngine
import com.example.data.SettingsRepository
import com.example.network.GeminiTtsClient

class AppContainer(application: Application) {
    val settingsRepository = SettingsRepository(application)
    val geminiTtsClient = GeminiTtsClient(application)
    val audioExporter = AudioExporter(application)
    val playbackEngine = PlaybackEngine(
        context = application,
        ttsClient = geminiTtsClient,
        settingsRepository = settingsRepository,
        exporter = audioExporter
    )
}

class ArLabsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
