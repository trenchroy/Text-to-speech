package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AudioExportFormat(val extension: String, val mimeType: String, val label: String) {
    WAV("wav", "audio/wav", "WAV (Lossless)"),
    M4A("m4a", "audio/mp4", "M4A (Compact AAC)")
}

class InsufficientStorageException : IOException("Phone mein storage kam hai!")

data class ExportedAudioResult(
    val fileName: String,
    val displayLocation: String,
    val uri: Uri?,
    val isMultiPart: Boolean = false,
    val partCount: Int = 1
)

data class ExportPartInput(
    val index: Int,
    val pcmFile: File,
    val trailingSilenceMs: Long
)

class AudioExporter(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 24000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_MS = (SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)) / 1000 // 48 bytes/ms
    }

    fun buildMeaningfulBaseName(scriptText: String): String {
        val cleaned = scriptText.trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString("_")
            .take(28)
            .ifBlank { "Script" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "AR_Labs_${cleaned}_$stamp"
    }

    suspend fun exportContinuous(
        parts: List<ExportPartInput>,
        scriptText: String,
        format: AudioExportFormat
    ): ExportedAudioResult = withContext(Dispatchers.IO) {
        val validParts = parts.filter { it.pcmFile.exists() && it.pcmFile.length() > 0L }
        if (validParts.isEmpty()) {
            throw IOException("Export karne ke liye audio ready nahi hai.")
        }

        val totalPcmBytes = validParts.sumOf {
            it.pcmFile.length() + (it.trailingSilenceMs * BYTES_PER_MS)
        }
        val estimatedOutputBytes = if (format == AudioExportFormat.WAV) {
            totalPcmBytes + 44L
        } else {
            (totalPcmBytes / 6L).coerceAtLeast(64_000L)
        }
        ensureEnoughStorage(estimatedOutputBytes + 2 * 1024 * 1024L)

        val baseName = buildMeaningfulBaseName(scriptText)
        val fileName = "$baseName.${format.extension}"

        // Render to temp file first so MediaStore / M4A muxer is atomic and fast
        val tempDir = File(context.cacheDir, "export_tmp").apply { mkdirs() }
        val tempFile = File(tempDir, fileName)
        if (tempFile.exists()) tempFile.delete()

        try {
            when (format) {
                AudioExportFormat.WAV -> writeWavFile(validParts, tempFile)
                AudioExportFormat.M4A -> writeM4aFile(validParts, tempFile)
            }
            saveToDownloads(tempFile, fileName, format.mimeType, subFolder = null)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun exportInParts(
        parts: List<ExportPartInput>,
        scriptText: String,
        format: AudioExportFormat
    ): ExportedAudioResult = withContext(Dispatchers.IO) {
        val validParts = parts.filter { it.pcmFile.exists() && it.pcmFile.length() > 0L }
        if (validParts.isEmpty()) {
            throw IOException("Export karne ke liye audio ready nahi hai.")
        }

        val totalPcmBytes = validParts.sumOf { it.pcmFile.length() }
        ensureEnoughStorage(totalPcmBytes + 2 * 1024 * 1024L)

        val baseName = buildMeaningfulBaseName(scriptText)
        val tempDir = File(context.cacheDir, "export_tmp").apply { mkdirs() }
        var lastUri: Uri? = null

        validParts.forEachIndexed { idx, part ->
            val partNumber = String.format(Locale.US, "%02d", idx + 1)
            val fileName = "${baseName}_Part_$partNumber.${format.extension}"
            val tempFile = File(tempDir, fileName)
            try {
                when (format) {
                    AudioExportFormat.WAV -> writeWavFile(listOf(part.copy(trailingSilenceMs = 0L)), tempFile)
                    AudioExportFormat.M4A -> writeM4aFile(listOf(part.copy(trailingSilenceMs = 0L)), tempFile)
                }
                val saved = saveToDownloads(tempFile, fileName, format.mimeType, subFolder = baseName)
                lastUri = saved.uri
            } finally {
                tempFile.delete()
            }
        }

        ExportedAudioResult(
            fileName = "${baseName}_Parts (${validParts.size} files)",
            displayLocation = "Downloads/AR Labs/$baseName",
            uri = lastUri,
            isMultiPart = true,
            partCount = validParts.size
        )
    }

    suspend fun prepareShareUri(
        parts: List<ExportPartInput>,
        scriptText: String,
        format: AudioExportFormat
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val validParts = parts.filter { it.pcmFile.exists() && it.pcmFile.length() > 0L }
        if (validParts.isEmpty()) {
            throw IOException("Share karne ke liye audio ready nahi hai.")
        }

        val shareDir = File(context.cacheDir, "shared").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val fileName = "${buildMeaningfulBaseName(scriptText)}.${format.extension}"
        val outFile = File(shareDir, fileName)

        when (format) {
            AudioExportFormat.WAV -> writeWavFile(validParts, outFile)
            AudioExportFormat.M4A -> writeM4aFile(validParts, outFile)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        uri to fileName
    }

    private fun ensureEnoughStorage(requiredBytes: Long) {
        val stat = StatFs(context.cacheDir.absolutePath)
        if (stat.availableBytes < requiredBytes) {
            throw InsufficientStorageException()
        }
    }

    private fun saveToDownloads(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        subFolder: String?
    ): ExportedAudioResult {
        val relativePath = if (subFolder.isNullOrBlank()) {
            "${Environment.DIRECTORY_DOWNLOADS}/AR Labs"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/AR Labs/$subFolder"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Downloads folder mein file nahi ban saki.")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 32 * 1024)
                    }
                } ?: throw IOException("Output stream open nahi ho saka.")
                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                ExportedAudioResult(
                    fileName = fileName,
                    displayLocation = relativePath,
                    uri = uri
                )
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val targetDir = if (subFolder.isNullOrBlank()) {
                File(baseDir, "AR Labs")
            } else {
                File(baseDir, "AR Labs/$subFolder")
            }.apply { mkdirs() }
            val destFile = File(targetDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
            ExportedAudioResult(
                fileName = fileName,
                displayLocation = targetDir.absolutePath,
                uri = uri
            )
        }
    }

    private fun writeWavFile(parts: List<ExportPartInput>, outFile: File) {
        val totalDataSize = parts.mapIndexed { idx, part ->
            val silenceBytes = if (idx < parts.lastIndex) part.trailingSilenceMs * BYTES_PER_MS else 0L
            part.pcmFile.length() + silenceBytes
        }.sum()

        BufferedOutputStream(FileOutputStream(outFile), 64 * 1024).use { out ->
            writeWavHeader(out, totalDataSize)
            val buffer = ByteArray(32 * 1024)
            parts.forEachIndexed { idx, part ->
                FileInputStream(part.pcmFile).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
                if (idx < parts.lastIndex && part.trailingSilenceMs > 0L) {
                    writeSilence(out, part.trailingSilenceMs * BYTES_PER_MS)
                }
            }
            out.flush()
        }
    }

    private fun writeWavHeader(out: OutputStream, pcmDataLength: Long) {
        val totalAudioLen = pcmDataLength
        val totalDataLen = totalAudioLen + 36
        val byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        out.write(header.array())
    }

    private fun writeSilence(out: OutputStream, silenceBytes: Long) {
        val zeros = ByteArray(4096)
        var remaining = silenceBytes
        while (remaining > 0) {
            val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
            out.write(zeros, 0, toWrite)
            remaining -= toWrite
        }
    }

    private fun writeM4aFile(parts: List<ExportPartInput>, outFile: File) {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var totalBytesFed = 0L

            var partIdx = 0
            var currentStream: FileInputStream? = null
            var silenceBytesRemaining = 0L
            val readBuf = ByteArray(4096)
            val zeros = ByteArray(4096)

            fun readNextPcmChunk(maxBytes: Int): ByteArray? {
                while (true) {
                    if (silenceBytesRemaining > 0L) {
                        val take = minOf(maxBytes.toLong(), silenceBytesRemaining, zeros.size.toLong()).toInt()
                        silenceBytesRemaining -= take
                        return zeros.copyOf(take)
                    }
                    if (partIdx >= parts.size) {
                        currentStream?.close()
                        currentStream = null
                        return null
                    }
                    if (currentStream == null) {
                        currentStream = FileInputStream(parts[partIdx].pcmFile)
                    }
                    val toRead = minOf(maxBytes, readBuf.size)
                    val read = currentStream!!.read(readBuf, 0, toRead)
                    if (read > 0) {
                        return readBuf.copyOf(read)
                    } else {
                        currentStream?.close()
                        currentStream = null
                        if (partIdx < parts.lastIndex) {
                            silenceBytesRemaining = parts[partIdx].trailingSilenceMs * BYTES_PER_MS
                        }
                        partIdx++
                    }
                }
            }

            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIdx)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                val chunk = readNextPcmChunk(inputBuffer.remaining())
                                if (chunk == null) {
                                    val ptsUs = (totalBytesFed / 2L) * 1_000_000L / SAMPLE_RATE
                                    codec.queueInputBuffer(
                                        inIdx,
                                        0,
                                        0,
                                        ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    inputBuffer.put(chunk)
                                    val ptsUs = (totalBytesFed / 2L) * 1_000_000L / SAMPLE_RATE
                                    totalBytesFed += chunk.size
                                    codec.queueInputBuffer(inIdx, 0, chunk.size, ptsUs, 0)
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) {
                                throw IOException("Audio format changed unexpectedly")
                            }
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val encodedData = codec.getOutputBuffer(outIdx)
                            if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                if (bufferInfo.size > 0 && muxerStarted) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            } finally {
                currentStream?.close()
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }
}
