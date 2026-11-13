package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.os.StatFs
import com.example.data.SettingsRepository
import com.example.domain.AudioPart
import com.example.domain.ScriptChunker
import com.example.domain.VoiceCatalog
import com.example.domain.VoiceProfile
import com.example.network.GeminiTtsClient
import com.example.network.TtsResult
import com.example.service.TtsPlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class SessionMode {
    IDLE,
    GENERATING_SINGLE,
    PLAYING_NON_STOP,
    READY_SINGLE
}

data class PlaybackUiState(
    val sessionMode: SessionMode = SessionMode.IDLE,
    val isNonStopSession: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isGenerating: Boolean = false,
    val hasActiveAudio: Boolean = false,
    val currentPartIndex: Int = 0,
    val totalParts: Int = 0,
    val readyPartsCount: Int = 0,
    val elapsedMs: Long = 0L,
    val estimatedTotalMs: Long = 0L,
    val bufferedTotalMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isRepeat: Boolean = false,
    val activeVoiceName: String = "",
    val activeScriptText: String = "",
    val activeSentenceStart: Int = -1,
    val activeSentenceEnd: Int = -1,
    val statusText: String = "",
    val previewingVoiceId: String? = null,
    val previewLoadingVoiceId: String? = null,
    val isExporting: Boolean = false
) {
    val bufferedFraction: Float
        get() = if (estimatedTotalMs <= 0L) 0f else (bufferedTotalMs.toFloat() / estimatedTotalMs.toFloat()).coerceIn(0f, 1f)

    val playbackFraction: Float
        get() = if (estimatedTotalMs <= 0L) 0f else (elapsedMs.toFloat() / estimatedTotalMs.toFloat()).coerceIn(0f, 1f)
}

sealed class PlaybackEvent {
    data class Toast(val message: String, val isError: Boolean = false) : PlaybackEvent()
    data class OpenSettings(val reason: String) : PlaybackEvent()
    data class ShareReady(val uri: android.net.Uri, val fileName: String, val mimeType: String) : PlaybackEvent()
}

class PlaybackEngine(
    private val context: Context,
    private val ttsClient: GeminiTtsClient,
    private val settingsRepository: SettingsRepository,
    private val exporter: AudioExporter
) {
    companion object {
        private const val SAMPLE_RATE = 24000
        private const val BYTES_PER_MS = 48L // 24000 * 2 bytes / 1000
        private const val PAUSE_SENTENCE_MS = 180L
        private const val PAUSE_PARAGRAPH_MS = 440L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var sessionJob: Job? = null
    private var playbackLoopJob: Job? = null
    private var previewJob: Job? = null

    private var sessionDir: File? = null
    private var parts: List<AudioPart> = emptyList()
    private val readyFiles = ConcurrentHashMap<Int, File>()
    private val actualDurationsMs = ConcurrentHashMap<Int, Long>()
    private var activeVoice: VoiceProfile = VoiceCatalog.voices.first()
    private var activeScript: String = ""

    private val playCursorPart = AtomicInteger(0)
    private val playCursorByteOffset = AtomicLong(0L)

    @Volatile
    private var shouldBePlaying: Boolean = false

    @Volatile
    private var seekRequested: Boolean = false

    @Volatile
    private var currentSpeed: Float = 1.0f

    @Volatile
    private var currentVolume: Float = 1.0f

    @Volatile
    private var currentRepeat: Boolean = false

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    private var previewTrack: AudioTrack? = null

    init {
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSpeed = settings.playbackSpeed
                currentVolume = settings.playbackVolume
                currentRepeat = settings.repeatEnabled
                _state.update {
                    it.copy(
                        speed = settings.playbackSpeed,
                        volume = settings.playbackVolume,
                        isRepeat = settings.repeatEnabled
                    )
                }
                applyAudioTrackParams()
            }
        }
    }

    fun startGeneration(
        scriptText: String,
        voice: VoiceProfile,
        nonStopMode: Boolean,
        apiKey: String
    ) {
        if (_state.value.isGenerating && !_state.value.isNonStopSession) {
            // Prevent duplicate requests while single-mode generation is running
            return
        }
        val trimmed = scriptText.trim()
        if (trimmed.isEmpty()) {
            _events.tryEmit(PlaybackEvent.Toast("Pehle script likhein ya paste karein!", isError = true))
            return
        }
        if (apiKey.isBlank()) {
            _events.tryEmit(PlaybackEvent.OpenSettings("Audio banane ke liye pehle apni Gemini API key dalein."))
            return
        }
        if (!ttsClient.isNetworkAvailable()) {
            _events.tryEmit(PlaybackEvent.Toast("Internet connection nahi hai. Network check karein.", isError = true))
            return
        }
        if (!hasEnoughCacheStorage()) {
            _events.tryEmit(PlaybackEvent.Toast("Phone mein storage kam hai! Kuch space free karein.", isError = true))
            return
        }

        stopPreview()
        teardownCurrentSession(keepService = false)

        val analysis = ScriptChunker.analyze(scriptText)
        if (analysis.parts.isEmpty()) {
            _events.tryEmit(PlaybackEvent.Toast("Pehle valid script likhein!", isError = true))
            return
        }

        parts = analysis.parts
        activeVoice = voice
        activeScript = scriptText
        val sDir = File(context.cacheDir, "tts_sessions/${UUID.randomUUID()}").apply { mkdirs() }
        sessionDir = sDir
        readyFiles.clear()
        actualDurationsMs.clear()
        playCursorPart.set(0)
        playCursorByteOffset.set(0L)

        if (nonStopMode) {
            shouldBePlaying = true
            _state.update {
                it.copy(
                    sessionMode = SessionMode.PLAYING_NON_STOP,
                    isNonStopSession = true,
                    isPlaying = true,
                    isBuffering = true,
                    isGenerating = true,
                    hasActiveAudio = true,
                    currentPartIndex = 0,
                    totalParts = parts.size,
                    readyPartsCount = 0,
                    elapsedMs = 0L,
                    estimatedTotalMs = analysis.estimatedTotalMs,
                    bufferedTotalMs = 0L,
                    activeVoiceName = voice.displayName,
                    activeScriptText = scriptText,
                    activeSentenceStart = parts.first().sentences.firstOrNull()?.startIndex ?: -1,
                    activeSentenceEnd = parts.first().sentences.firstOrNull()?.endIndex ?: -1,
                    statusText = "Part 1 taiyaar ho raha hai..."
                )
            }
            startForegroundService()
            launchNonStopSession(apiKey, sDir)
        } else {
            shouldBePlaying = false
            _state.update {
                it.copy(
                    sessionMode = SessionMode.GENERATING_SINGLE,
                    isNonStopSession = false,
                    isPlaying = false,
                    isBuffering = true,
                    isGenerating = true,
                    hasActiveAudio = false,
                    currentPartIndex = 0,
                    totalParts = parts.size,
                    readyPartsCount = 0,
                    elapsedMs = 0L,
                    estimatedTotalMs = analysis.estimatedTotalMs,
                    bufferedTotalMs = 0L,
                    activeVoiceName = voice.displayName,
                    activeScriptText = scriptText,
                    activeSentenceStart = -1,
                    activeSentenceEnd = -1,
                    statusText = "Audio ban raha hai..."
                )
            }
            launchSingleGenerationSession(apiKey, sDir)
        }
    }

    private fun launchNonStopSession(apiKey: String, sDir: File) {
        sessionJob = scope.launch {
            val semaphore = Semaphore(2)
            // Start producer loop prioritizing parts ahead of playCursorPart
            val producerJob = launch {
                var nextIndexToSchedule = 0
                while (isActive && nextIndexToSchedule < parts.size) {
                    val cursor = playCursorPart.get()
                    // Keep up to 6 parts ahead buffered, or steadily fill all parts
                    while (nextIndexToSchedule > cursor + 6 && isActive) {
                        delay(200)
                    }
                    val idx = nextIndexToSchedule
                    nextIndexToSchedule++
                    semaphore.withPermit {
                        if (!isActive) return@withPermit
                        val ok = synthesizePartWithAutoRetry(apiKey, parts[idx], sDir)
                        if (!ok) {
                            // Fatal non-retryable error (e.g. invalid key or safety block)
                            return@launch
                        }
                    }
                }
                if (isActive && readyFiles.size == parts.size) {
                    _state.update {
                        it.copy(
                            isGenerating = false,
                            statusText = "Poori script buffered hai"
                        )
                    }
                }
            }

            playbackLoopJob = launch {
                runContinuousAudioLoop()
            }

            producerJob.join()
        }
    }

    private fun launchSingleGenerationSession(apiKey: String, sDir: File) {
        sessionJob = scope.launch {
            for (part in parts) {
                if (!isActive) return@launch
                _state.update {
                    it.copy(statusText = "Part ${part.index + 1} / ${parts.size} ban raha hai...")
                }
                val result = synthesizeSingleAttemptOrRetry(apiKey, part, sDir)
                if (result != null) {
                    _events.tryEmit(PlaybackEvent.Toast(result, isError = true))
                    teardownCurrentSession(keepService = false)
                    return@launch
                }
            }

            // All parts ready -> open Custom Player and start playing immediately
            shouldBePlaying = true
            _state.update {
                it.copy(
                    sessionMode = SessionMode.READY_SINGLE,
                    isGenerating = false,
                    isBuffering = false,
                    hasActiveAudio = true,
                    isPlaying = true,
                    currentPartIndex = 0,
                    elapsedMs = 0L,
                    estimatedTotalMs = computeTotalDurationMs(),
                    bufferedTotalMs = computeBufferedDurationMs(),
                    statusText = "Audio taiyaar hai!"
                )
            }
            startForegroundService()
            playbackLoopJob = launch {
                runContinuousAudioLoop()
            }
        }
    }

    private suspend fun synthesizeSingleAttemptOrRetry(
        apiKey: String,
        part: AudioPart,
        sDir: File
    ): String? {
        var attempt = 0
        while (attempt < 3) {
            when (val res = ttsClient.synthesizeSpeech(apiKey, part.text, activeVoice)) {
                is TtsResult.Success -> {
                    savePartPcm(part, res.pcmBytes, sDir)
                    return null
                }
                is TtsResult.Error -> {
                    if (!res.isRetryable) {
                        if (res is TtsResult.Error.InvalidKey) {
                            _events.tryEmit(PlaybackEvent.OpenSettings(res.hinglishMessage))
                        }
                        return res.hinglishMessage
                    }
                    attempt++
                    if (attempt >= 3) return res.hinglishMessage
                    val waitMs = if (res is TtsResult.Error.RateLimit && res.retryAfterSeconds != null) {
                        (res.retryAfterSeconds * 1000L).coerceIn(1500L, 12000L)
                    } else {
                        1500L * attempt
                    }
                    delay(waitMs)
                }
            }
        }
        return "Audio generate nahi ho saka."
    }

    private suspend fun synthesizePartWithAutoRetry(
        apiKey: String,
        part: AudioPart,
        sDir: File
    ): Boolean {
        if (readyFiles.containsKey(part.index)) return true
        var attempt = 0
        while (scope.isActive && sessionDir == sDir) {
            if (!ttsClient.isNetworkAvailable()) {
                _state.update { it.copy(statusText = "Internet ka intezaar hai... auto-resume hoga") }
                delay(2000)
                continue
            }

            when (val res = ttsClient.synthesizeSpeech(apiKey, part.text, activeVoice)) {
                is TtsResult.Success -> {
                    savePartPcm(part, res.pcmBytes, sDir)
                    return true
                }
                is TtsResult.Error -> {
                    if (!res.isRetryable) {
                        _events.tryEmit(PlaybackEvent.Toast(res.hinglishMessage, isError = true))
                        if (res is TtsResult.Error.InvalidKey) {
                            _events.tryEmit(PlaybackEvent.OpenSettings(res.hinglishMessage))
                            teardownCurrentSession(keepService = false)
                        }
                        return false
                    }
                    attempt++
                    val backoffMs = when {
                        res is TtsResult.Error.RateLimit && res.retryAfterSeconds != null ->
                            (res.retryAfterSeconds * 1000L).coerceIn(2000L, 15000L)
                        res is TtsResult.Error.RateLimit ->
                            minOf(15000L, 2000L * attempt)
                        else ->
                            minOf(10000L, 1200L * attempt)
                    }
                    if (playCursorPart.get() >= part.index) {
                        _state.update {
                            it.copy(
                                isBuffering = true,
                                statusText = if (res is TtsResult.Error.RateLimit) {
                                    "Limit pause — auto-retry chal raha hai..."
                                } else {
                                    "Buffering..."
                                }
                            )
                        }
                    }
                    delay(backoffMs)
                }
            }
        }
        return false
    }

    private fun savePartPcm(part: AudioPart, pcmBytes: ByteArray, sDir: File) {
        val file = File(sDir, "part_${part.index}.pcm")
        file.writeBytes(pcmBytes)
        readyFiles[part.index] = file
        val durationMs = (pcmBytes.size / BYTES_PER_MS).coerceAtLeast(200L) + pauseMsForPart(part)
        actualDurationsMs[part.index] = durationMs

        _state.update {
            it.copy(
                readyPartsCount = readyFiles.size,
                estimatedTotalMs = computeTotalDurationMs(),
                bufferedTotalMs = computeBufferedDurationMs()
            )
        }
    }

    private fun pauseMsForPart(part: AudioPart): Long {
        if (part.index >= parts.lastIndex) return 0L
        return if (part.endsWithParagraph) PAUSE_PARAGRAPH_MS else PAUSE_SENTENCE_MS
    }

    private suspend fun runContinuousAudioLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeAudioTrack = track
        applyAudioTrackParams()

        val buffer = ByteArray(4096)

        try {
            while (scope.isActive && parts.isNotEmpty()) {
                if (!shouldBePlaying) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                    }
                    delay(80)
                    continue
                }

                val partIdx = playCursorPart.get()
                if (partIdx >= parts.size) {
                    if (currentRepeat) {
                        playCursorPart.set(0)
                        playCursorByteOffset.set(0L)
                        continue
                    } else {
                        shouldBePlaying = false
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                isBuffering = false,
                                elapsedMs = it.estimatedTotalMs,
                                activeSentenceStart = -1,
                                activeSentenceEnd = -1,
                                statusText = "Script poori ho gayi!"
                            )
                        }
                        updateNotification()
                        delay(120)
                        continue
                    }
                }

                val pcmFile = readyFiles[partIdx]
                if (pcmFile == null || !pcmFile.exists()) {
                    _state.update {
                        it.copy(
                            isBuffering = true,
                            currentPartIndex = partIdx,
                            statusText = "Buffering..."
                        )
                    }
                    updateNotification()
                    delay(120)
                    continue
                }

                // Ready to play this part
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = true,
                        currentPartIndex = partIdx,
                        statusText = if (it.isNonStopSession) "Part ${partIdx + 1} of ${parts.size}" else "Playing"
                    )
                }
                updateNotification()

                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }

                val part = parts[partIdx]
                val totalFileBytes = pcmFile.length()
                var offset = playCursorByteOffset.get().coerceIn(0L, totalFileBytes)
                // Align to 2-byte PCM sample boundary
                offset = offset and -2L

                seekRequested = false
                var finishedPartNormally = true

                FileInputStream(pcmFile).use { fis ->
                    if (offset > 0L) {
                        var skipped = 0L
                        while (skipped < offset) {
                            val s = fis.skip(offset - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }

                    while (scope.isActive) {
                        if (!shouldBePlaying || seekRequested) {
                            finishedPartNormally = false
                            break
                        }
                        val read = fis.read(buffer)
                        if (read <= 0) break

                        var written = 0
                        while (written < read && shouldBePlaying && !seekRequested && scope.isActive) {
                            val w = track.write(buffer, written, read - written, AudioTrack.WRITE_BLOCKING)
                            if (w < 0) break
                            written += w
                        }
                        offset += written
                        playCursorByteOffset.set(offset)
                        updatePlaybackTelemetry(part, offset, totalFileBytes)
                    }
                }

                if (seekRequested) {
                    track.pause()
                    track.flush()
                    seekRequested = false
                    continue
                }

                if (!shouldBePlaying) {
                    track.pause()
                    continue
                }

                if (finishedPartNormally) {
                    // Add natural short silence between parts for human breathing pause
                    val silenceMs = pauseMsForPart(part)
                    if (silenceMs > 0L && shouldBePlaying && !seekRequested) {
                        val silenceBytesTotal = (silenceMs * BYTES_PER_MS).toInt()
                        val zeros = ByteArray(minOf(silenceBytesTotal, 4096))
                        var sent = 0
                        while (sent < silenceBytesTotal && shouldBePlaying && !seekRequested && scope.isActive) {
                            val chunk = minOf(zeros.size, silenceBytesTotal - sent)
                            val w = track.write(zeros, 0, chunk, AudioTrack.WRITE_BLOCKING)
                            if (w <= 0) break
                            sent += w
                        }
                    }

                    if (!seekRequested && shouldBePlaying) {
                        playCursorPart.incrementAndGet()
                        playCursorByteOffset.set(0L)
                    }
                }
            }
        } catch (_: CancellationException) {
            // Normal teardown
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            }
            if (activeAudioTrack == track) {
                activeAudioTrack = null
            }
        }
    }

    private fun updatePlaybackTelemetry(part: AudioPart, byteOffset: Long, totalBytes: Long) {
        val partElapsedMs = byteOffset / BYTES_PER_MS
        val elapsedBefore = (0 until part.index).sumOf { idx ->
            actualDurationsMs[idx] ?: parts.getOrNull(idx)?.estimatedDurationMs ?: 0L
        }

        // Map byte offset within part to exact sentence span
        val progress = if (totalBytes > 0L) (byteOffset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        val sentences = part.sentences
        var activeStart = -1
        var activeEnd = -1
        if (sentences.isNotEmpty()) {
            val totalChars = sentences.sumOf { it.text.length.coerceAtLeast(1) }
            var acc = 0f
            for (s in sentences) {
                val share = s.text.length.coerceAtLeast(1).toFloat() / totalChars.toFloat()
                if (progress <= acc + share + 0.01f) {
                    activeStart = s.startIndex
                    activeEnd = s.endIndex
                    break
                }
                acc += share
            }
            if (activeStart == -1) {
                activeStart = sentences.last().startIndex
                activeEnd = sentences.last().endIndex
            }
        }

        _state.update {
            it.copy(
                elapsedMs = elapsedBefore + partElapsedMs,
                activeSentenceStart = activeStart,
                activeSentenceEnd = activeEnd
            )
        }
    }

    fun togglePlayPause() {
        if (!_state.value.hasActiveAudio) return
        if (shouldBePlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        shouldBePlaying = false
        runCatching { activeAudioTrack?.pause() }
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
        updateNotification()
    }

    fun resume() {
        if (!_state.value.hasActiveAudio) return
        // If already finished at the end, restart from beginning
        if (playCursorPart.get() >= parts.size) {
            playCursorPart.set(0)
            playCursorByteOffset.set(0L)
        }
        shouldBePlaying = true
        _state.update { it.copy(isPlaying = true) }
        startForegroundService()
        updateNotification()
    }

    fun nextPart() {
        if (parts.isEmpty()) return
        val next = (playCursorPart.get() + 1).coerceAtMost(parts.lastIndex)
        playCursorPart.set(next)
        playCursorByteOffset.set(0L)
        seekRequested = true
        if (!shouldBePlaying) {
            shouldBePlaying = true
        }
        _state.update {
            it.copy(
                currentPartIndex = next,
                isPlaying = true,
                elapsedMs = durationBeforePart(next)
            )
        }
        updateNotification()
    }

    fun previousPart() {
        if (parts.isEmpty()) return
        val currentOffsetMs = playCursorByteOffset.get() / BYTES_PER_MS
        val target = if (currentOffsetMs > 2500L) {
            playCursorPart.get()
        } else {
            (playCursorPart.get() - 1).coerceAtLeast(0)
        }
        playCursorPart.set(target)
        playCursorByteOffset.set(0L)
        seekRequested = true
        if (!shouldBePlaying) {
            shouldBePlaying = true
        }
        _state.update {
            it.copy(
                currentPartIndex = target,
                isPlaying = true,
                elapsedMs = durationBeforePart(target)
            )
        }
        updateNotification()
    }

    fun seekTo(targetMs: Long) {
        if (parts.isEmpty()) return
        val clampedMs = targetMs.coerceIn(0L, computeTotalDurationMs())
        var accMs = 0L
        var targetPart = 0
        var offsetInPartMs = 0L

        for (i in parts.indices) {
            val partDur = actualDurationsMs[i] ?: parts[i].estimatedDurationMs
            if (clampedMs <= accMs + partDur || i == parts.lastIndex) {
                targetPart = i
                offsetInPartMs = (clampedMs - accMs).coerceAtLeast(0L)
                break
            }
            accMs += partDur
        }

        // Only seek into ready parts if streaming
        if (!readyFiles.containsKey(targetPart)) {
            val maxReady = readyFiles.keys.maxOrNull() ?: 0
            targetPart = maxReady
            offsetInPartMs = 0L
        }

        val maxFileBytes = readyFiles[targetPart]?.length() ?: 0L
        val targetByte = (offsetInPartMs * BYTES_PER_MS).coerceIn(0L, maxFileBytes) and -2L
        playCursorPart.set(targetPart)
        playCursorByteOffset.set(targetByte)
        seekRequested = true

        _state.update {
            it.copy(
                currentPartIndex = targetPart,
                elapsedMs = clampedMs
            )
        }
        updateNotification()
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 1.5f)
        currentSpeed = clamped
        scope.launch { settingsRepository.setPlaybackSpeed(clamped) }
        _state.update { it.copy(speed = clamped) }
        applyAudioTrackParams()
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        currentVolume = clamped
        scope.launch { settingsRepository.setPlaybackVolume(clamped) }
        _state.update { it.copy(volume = clamped) }
        applyAudioTrackParams()
    }

    fun setRepeat(enabled: Boolean) {
        currentRepeat = enabled
        scope.launch { settingsRepository.setRepeatEnabled(enabled) }
        _state.update { it.copy(isRepeat = enabled) }
    }

    fun stop() {
        teardownCurrentSession(keepService = false)
    }

    fun previewVoice(voice: VoiceProfile, apiKey: String) {
        if (_state.value.previewingVoiceId == voice.id) {
            stopPreview()
            return
        }
        if (apiKey.isBlank()) {
            _events.tryEmit(PlaybackEvent.OpenSettings("Voice preview sunne ke liye pehle Gemini API key dalein."))
            return
        }
        if (!ttsClient.isNetworkAvailable()) {
            _events.tryEmit(PlaybackEvent.Toast("Internet connection nahi hai. Network check karein.", isError = true))
            return
        }

        stopPreview()
        // Pause main playback smoothly while previewing
        if (shouldBePlaying) pause()

        previewJob = scope.launch {
            _state.update { it.copy(previewLoadingVoiceId = voice.id, previewingVoiceId = null) }
            val previewDir = File(context.cacheDir, "voice_previews").apply { mkdirs() }
            val cachedFile = File(previewDir, "${voice.id}.pcm")

            val pcmBytes = if (cachedFile.exists() && cachedFile.length() > 0L) {
                cachedFile.readBytes()
            } else {
                when (val res = ttsClient.synthesizeSpeech(apiKey, voice.previewSampleText, voice)) {
                    is TtsResult.Success -> {
                        cachedFile.writeBytes(res.pcmBytes)
                        res.pcmBytes
                    }
                    is TtsResult.Error -> {
                        _state.update { it.copy(previewLoadingVoiceId = null, previewingVoiceId = null) }
                        if (res is TtsResult.Error.InvalidKey) {
                            _events.tryEmit(PlaybackEvent.OpenSettings(res.hinglishMessage))
                        } else {
                            _events.tryEmit(PlaybackEvent.Toast(res.hinglishMessage, isError = true))
                        }
                        return@launch
                    }
                }
            }

            if (!isActive) return@launch
            _state.update { it.copy(previewLoadingVoiceId = null, previewingVoiceId = voice.id) }
            playPcmOneShot(pcmBytes)
            _state.update { it.copy(previewingVoiceId = null) }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        runCatching {
            previewTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        }
        previewTrack = null
        _state.update { it.copy(previewingVoiceId = null, previewLoadingVoiceId = null) }
    }

    private fun playPcmOneShot(pcmBytes: ByteArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        previewTrack = track
        try {
            track.setVolume(currentVolume)
            track.play()
            var offset = 0
            while (offset < pcmBytes.size && previewJob?.isActive == true) {
                val len = minOf(4096, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, len, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            }
            if (previewTrack == track) previewTrack = null
        }
    }

    fun exportAudio(format: AudioExportFormat, inParts: Boolean) {
        val exportInputs = buildReadyExportInputs()
        if (exportInputs.isEmpty()) {
            _events.tryEmit(PlaybackEvent.Toast("Abhi koi ready audio nahi hai export ke liye.", isError = true))
            return
        }
        scope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val result = if (inParts) {
                    exporter.exportInParts(exportInputs, activeScript, format)
                } else {
                    exporter.exportContinuous(exportInputs, activeScript, format)
                }
                val msg = if (result.isMultiPart) {
                    "${result.partCount} parts save ho gaye: ${result.displayLocation}"
                } else {
                    "Save ho gaya: ${result.fileName} (${result.displayLocation})"
                }
                _events.tryEmit(PlaybackEvent.Toast(msg, isError = false))
            } catch (_: InsufficientStorageException) {
                _events.tryEmit(PlaybackEvent.Toast("Phone mein storage kam hai! Kuch space free karke dobara save karein.", isError = true))
            } catch (e: Exception) {
                _events.tryEmit(PlaybackEvent.Toast("Save nahi ho saka: ${e.localizedMessage ?: "Error"}", isError = true))
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    fun shareAudio(format: AudioExportFormat = AudioExportFormat.M4A) {
        val exportInputs = buildReadyExportInputs()
        if (exportInputs.isEmpty()) {
            _events.tryEmit(PlaybackEvent.Toast("Abhi koi ready audio nahi hai share ke liye.", isError = true))
            return
        }
        scope.launch {
            _state.update { it.copy(isExporting = true) }
            try {
                val (uri, fileName) = exporter.prepareShareUri(exportInputs, activeScript, format)
                _events.tryEmit(PlaybackEvent.ShareReady(uri, fileName, format.mimeType))
            } catch (_: InsufficientStorageException) {
                _events.tryEmit(PlaybackEvent.Toast("Phone mein storage kam hai! Share file nahi ban saki.", isError = true))
            } catch (e: Exception) {
                _events.tryEmit(PlaybackEvent.Toast("Share nahi ho saka: ${e.localizedMessage ?: "Error"}", isError = true))
            } finally {
                _state.update { it.copy(isExporting = false) }
            }
        }
    }

    fun clearAllCache() {
        stop()
        stopPreview()
        File(context.cacheDir, "tts_sessions").deleteRecursively()
        File(context.cacheDir, "voice_previews").deleteRecursively()
        File(context.cacheDir, "shared").deleteRecursively()
        _events.tryEmit(PlaybackEvent.Toast("Sabhi temporary audio cache saaf ho gaya!"))
    }

    private fun buildReadyExportInputs(): List<ExportPartInput> {
        return parts.mapNotNull { part ->
            val file = readyFiles[part.index] ?: return@mapNotNull null
            ExportPartInput(
                index = part.index,
                pcmFile = file,
                trailingSilenceMs = pauseMsForPart(part)
            )
        }
    }

    private fun applyAudioTrackParams() {
        val track = activeAudioTrack ?: return
        runCatching {
            track.setVolume(currentVolume)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track.playbackParams = PlaybackParams().setSpeed(currentSpeed)
            }
        }
    }

    private fun durationBeforePart(partIndex: Int): Long {
        return (0 until partIndex).sumOf { idx ->
            actualDurationsMs[idx] ?: parts.getOrNull(idx)?.estimatedDurationMs ?: 0L
        }
    }

    private fun computeTotalDurationMs(): Long {
        return parts.indices.sumOf { idx ->
            actualDurationsMs[idx] ?: parts[idx].estimatedDurationMs
        }
    }

    private fun computeBufferedDurationMs(): Long {
        return readyFiles.keys.sumOf { idx ->
            actualDurationsMs[idx] ?: parts.getOrNull(idx)?.estimatedDurationMs ?: 0L
        }
    }

    private fun hasEnoughCacheStorage(): Boolean {
        val stat = StatFs(context.cacheDir.absolutePath)
        return stat.availableBytes > 5L * 1024L * 1024L
    }

    private fun teardownCurrentSession(keepService: Boolean) {
        shouldBePlaying = false
        sessionJob?.cancel()
        sessionJob = null
        playbackLoopJob?.cancel()
        playbackLoopJob = null
        runCatching {
            activeAudioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        }
        activeAudioTrack = null
        readyFiles.clear()
        actualDurationsMs.clear()
        sessionDir?.deleteRecursively()
        sessionDir = null

        _state.update {
            PlaybackUiState(
                speed = currentSpeed,
                volume = currentVolume,
                isRepeat = currentRepeat
            )
        }
        if (!keepService) {
            stopForegroundService()
        }
    }

    private fun startForegroundService() {
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateNotification() {
        if (!_state.value.hasActiveAudio) return
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_UPDATE
        }
        runCatching { context.startService(intent) }
    }

    private fun stopForegroundService() {
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_STOP
        }
        runCatching { context.stopService(intent) }
    }
}
