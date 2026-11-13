package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.audio.AudioExportFormat
import com.example.audio.PlaybackEvent
import com.example.audio.PlaybackUiState
import com.example.audio.SessionMode
import com.example.data.ThemeMode
import com.example.data.UserSettings
import com.example.domain.ScriptAnalysis
import com.example.domain.ScriptChunker
import com.example.domain.VoiceCatalog
import com.example.domain.VoiceProfile
import com.example.network.KeyTestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false
)

enum class KeyTestStatus {
    IDLE,
    TESTING,
    VALID,
    INVALID
}

data class MainUiState(
    val showSplash: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val settingsBannerMessage: String? = null,
    val scriptText: String = "",
    val scriptAnalysis: ScriptAnalysis = ScriptChunker.analyze(""),
    val sampleCycleIndex: Int = 0,
    val isVoiceSheetOpen: Boolean = false,
    val isDownloadSheetOpen: Boolean = false,
    val isEditingWhilePlaying: Boolean = false,
    val keyTestStatus: KeyTestStatus = KeyTestStatus.IDLE,
    val keyTestFeedback: String = "",
    val activeToast: ToastMessage? = null
)

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val settingsRepo = container.settingsRepository
    private val ttsClient = container.geminiTtsClient
    private val playbackEngine = container.playbackEngine

    val settings: StateFlow<UserSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val playbackState: StateFlow<PlaybackUiState> = playbackEngine.state

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<PlaybackEvent.ShareReady>(extraBufferCapacity = 4)
    val shareEvents = _shareEvents.asSharedFlow()

    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            delay(2000L)
            _uiState.update { it.copy(showSplash = false) }
        }

        viewModelScope.launch {
            playbackEngine.events.collect { event ->
                when (event) {
                    is PlaybackEvent.Toast -> showToast(event.message, event.isError)
                    is PlaybackEvent.OpenSettings -> {
                        _uiState.update {
                            it.copy(
                                isSettingsOpen = true,
                                settingsBannerMessage = event.reason,
                                isVoiceSheetOpen = false
                            )
                        }
                        showToast(event.reason, isError = true)
                    }
                    is PlaybackEvent.ShareReady -> _shareEvents.emit(event)
                }
            }
        }
    }

    fun updateScriptText(text: String) {
        val analysis = ScriptChunker.analyze(text)
        _uiState.update { it.copy(scriptText = text, scriptAnalysis = analysis) }
    }

    fun clearScript() {
        if (_uiState.value.scriptText.isEmpty()) return
        playbackEngine.stop()
        _uiState.update {
            it.copy(
                scriptText = "",
                scriptAnalysis = ScriptChunker.analyze(""),
                isEditingWhilePlaying = false
            )
        }
        showToast("Script saaf kar di gayi.")
    }

    fun magicFill() {
        val samples = ScriptChunker.sampleScripts
        val idx = _uiState.value.sampleCycleIndex % samples.size
        val sample = samples[idx]
        if (playbackState.value.hasActiveAudio) {
            playbackEngine.stop()
        }
        val analysis = ScriptChunker.analyze(sample.content)
        _uiState.update {
            it.copy(
                scriptText = sample.content,
                scriptAnalysis = analysis,
                sampleCycleIndex = idx + 1,
                isEditingWhilePlaying = false
            )
        }
        showToast("${sample.language.label} sample script add ho gayi!")
    }

    fun selectVoice(voice: VoiceProfile) {
        viewModelScope.launch {
            settingsRepo.setSelectedVoiceId(voice.id)
        }
        _uiState.update { it.copy(isVoiceSheetOpen = false) }
    }

    fun setVoiceSheetOpen(open: Boolean) {
        if (!open) {
            playbackEngine.stopPreview()
        }
        _uiState.update { it.copy(isVoiceSheetOpen = open) }
    }

    fun previewVoice(voice: VoiceProfile) {
        val apiKey = settings.value.apiKey
        playbackEngine.previewVoice(voice, apiKey)
    }

    fun setNonStopEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setNonStopEnabled(enabled)
        }
    }

    fun toggleThemeQuick(currentlyDark: Boolean) {
        val target = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch {
            settingsRepo.setThemeMode(target)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepo.setThemeMode(mode)
        }
    }

    fun openSettings(bannerMessage: String? = null) {
        _uiState.update {
            it.copy(
                isSettingsOpen = true,
                settingsBannerMessage = bannerMessage,
                keyTestStatus = KeyTestStatus.IDLE,
                keyTestFeedback = ""
            )
        }
    }

    fun closeSettings() {
        _uiState.update {
            it.copy(
                isSettingsOpen = false,
                settingsBannerMessage = null
            )
        }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        viewModelScope.launch {
            settingsRepo.saveApiKey(trimmed)
            if (trimmed.isEmpty()) {
                showToast("API key hata di gayi.")
            } else {
                showToast("Gemini API key surakshit save ho gayi!")
                _uiState.update { it.copy(settingsBannerMessage = null) }
            }
        }
    }

    fun testApiKey(keyInput: String) {
        val key = keyInput.trim()
        if (key.isEmpty()) {
            _uiState.update {
                it.copy(
                    keyTestStatus = KeyTestStatus.INVALID,
                    keyTestFeedback = "Pehle apni Gemini API key paste karein!"
                )
            }
            showToast("Pehle apni Gemini API key paste karein!", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(keyTestStatus = KeyTestStatus.TESTING, keyTestFeedback = "Key check ho rahi hai...")
            }
            when (val res = ttsClient.testApiKey(key)) {
                is KeyTestResult.Valid -> {
                    settingsRepo.saveApiKey(key)
                    _uiState.update {
                        it.copy(
                            keyTestStatus = KeyTestStatus.VALID,
                            keyTestFeedback = "Key bilkul sahi hai! Save bhi ho gayi.",
                            settingsBannerMessage = null
                        )
                    }
                    showToast("API key verified aur save ho gayi!")
                }
                is KeyTestResult.Invalid -> {
                    _uiState.update {
                        it.copy(
                            keyTestStatus = KeyTestStatus.INVALID,
                            keyTestFeedback = res.hinglishMessage
                        )
                    }
                    showToast(res.hinglishMessage, isError = true)
                }
            }
        }
    }

    fun onGenerateOrPlayClicked() {
        val pb = playbackState.value
        if (pb.sessionMode == SessionMode.GENERATING_SINGLE) {
            return
        }
        val text = _uiState.value.scriptText
        if (text.trim().isEmpty()) {
            showToast("Pehle script likhein ya paste karein!", isError = true)
            return
        }
        val currentSettings = settings.value
        if (currentSettings.apiKey.isBlank()) {
            openSettings("Shuru karne ke liye pehle apni Gemini API key dalein!")
            showToast("API key nahi mili — Settings khul rahi hai", isError = true)
            return
        }
        val voice = VoiceCatalog.byId(currentSettings.selectedVoiceId)
        _uiState.update { it.copy(isEditingWhilePlaying = false) }
        playbackEngine.startGeneration(
            scriptText = text,
            voice = voice,
            nonStopMode = currentSettings.nonStopEnabled,
            apiKey = currentSettings.apiKey
        )
    }

    fun togglePlayPause() = playbackEngine.togglePlayPause()
    fun nextPart() = playbackEngine.nextPart()
    fun previousPart() = playbackEngine.previousPart()
    fun seekTo(ms: Long) = playbackEngine.seekTo(ms)
    fun setSpeed(speed: Float) = playbackEngine.setSpeed(speed)
    fun setVolume(volume: Float) = playbackEngine.setVolume(volume)
    fun setRepeat(enabled: Boolean) = playbackEngine.setRepeat(enabled)
    fun stopPlayback() {
        playbackEngine.stop()
        _uiState.update { it.copy(isEditingWhilePlaying = false, isDownloadSheetOpen = false) }
    }

    fun setEditingWhilePlaying(editing: Boolean) {
        if (editing) {
            playbackEngine.stop()
        }
        _uiState.update { it.copy(isEditingWhilePlaying = editing) }
    }

    fun setDownloadSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isDownloadSheetOpen = open) }
    }

    fun exportAudio(format: AudioExportFormat, inParts: Boolean) {
        _uiState.update { it.copy(isDownloadSheetOpen = false) }
        playbackEngine.exportAudio(format = format, inParts = inParts)
    }

    fun shareAudio(format: AudioExportFormat = AudioExportFormat.M4A) {
        playbackEngine.shareAudio(format = format)
    }

    fun clearCache() {
        playbackEngine.clearAllCache()
    }

    fun showToast(message: String, isError: Boolean = false) {
        toastJob?.cancel()
        val toast = ToastMessage(text = message, isError = isError)
        _uiState.update { it.copy(activeToast = toast) }
        toastJob = viewModelScope.launch {
            delay(3400L)
            _uiState.update { current ->
                if (current.activeToast?.id == toast.id) current.copy(activeToast = null) else current
            }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(activeToast = null) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
