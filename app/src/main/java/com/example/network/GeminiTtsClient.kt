package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import com.example.domain.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class TtsResult {
    data class Success(
        val pcmBytes: ByteArray,
        val sampleRate: Int = 24000
    ) : TtsResult()

    sealed class Error(val hinglishMessage: String, val isRetryable: Boolean) : TtsResult() {
        data class InvalidKey(val detail: String = "") : Error(
            hinglishMessage = "API key galat hai ya invalid hai. Settings mein sahi Gemini key dalein.",
            isRetryable = false
        )

        data class RateLimit(val retryAfterSeconds: Long? = null) : Error(
            hinglishMessage = "Gemini ki rate limit reach ho gayi hai. Thodi der mein auto-retry hoga.",
            isRetryable = true
        )

        data object NoInternet : Error(
            hinglishMessage = "Internet connection nahi mil raha. Network check karein.",
            isRetryable = true
        )

        data object BlockedContent : Error(
            hinglishMessage = "Ye content Gemini safety rules ki wajah se block hua hai. Script thodi badal kar dekhein.",
            isRetryable = false
        )

        data class ServerError(val code: Int, val detail: String = "") : Error(
            hinglishMessage = "Gemini server abhi busy hai ($code). Phir se try kar rahe hain...",
            isRetryable = true
        )

        data class Unknown(val detail: String) : Error(
            hinglishMessage = "Audio banane mein dikkat aayi: $detail",
            isRetryable = true
        )
    }
}

sealed class KeyTestResult {
    data object Valid : KeyTestResult()
    data class Invalid(val hinglishMessage: String) : KeyTestResult()
}

class GeminiTtsClient(private val context: Context) {

    companion object {
        private const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun testApiKey(apiKey: String): KeyTestResult = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return@withContext KeyTestResult.Invalid("Pehle Gemini API key paste karein!")
        }
        if (!isNetworkAvailable()) {
            return@withContext KeyTestResult.Invalid("Internet connection nahi hai. Network on karein.")
        }

        val request = Request.Builder()
            .url("$BASE_URL/models?pageSize=1")
            .addHeader("x-goog-api-key", trimmed)
            .get()
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    KeyTestResult.Valid
                } else {
                    when (response.code) {
                        400, 401, 403 -> KeyTestResult.Invalid(
                            "API key galat ya expire ho gayi hai. Nayi key paste karein."
                        )
                        429 -> KeyTestResult.Invalid(
                            "Key valid lagti hai par abhi rate limit full hai. Kuch seconds baad try karein."
                        )
                        else -> {
                            val errMsg = parseApiErrorMessage(bodyStr)
                            KeyTestResult.Invalid(
                                if (errMsg.isNotBlank()) "Key verify nahi hui: $errMsg"
                                else "Server error (${response.code}). Baad mein try karein."
                            )
                        }
                    }
                }
            }
        } catch (_: UnknownHostException) {
            KeyTestResult.Invalid("Internet connection nahi hai. Network check karein.")
        } catch (_: SocketTimeoutException) {
            KeyTestResult.Invalid("Network bahut slow hai. Dobara Test karein.")
        } catch (e: IOException) {
            KeyTestResult.Invalid("Network error: ${e.localizedMessage ?: "Connection failed"}")
        }
    }

    suspend fun synthesizeSpeech(
        apiKey: String,
        text: String,
        voice: VoiceProfile
    ): TtsResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext TtsResult.Error.InvalidKey("Missing API key")
        }
        if (!isNetworkAvailable()) {
            return@withContext TtsResult.Error.NoInternet
        }

        val promptText = "${voice.stylePrompt}\n\nRead the following script aloud exactly as written, without translating or adding extra commentary:\n\n$text"

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", promptText))
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig",
                        JSONObject().apply {
                            put(
                                "voiceConfig",
                                JSONObject().apply {
                                    put(
                                        "prebuiltVoiceConfig",
                                        JSONObject().put("voiceName", voice.geminiVoiceName)
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/models/$TTS_MODEL:generateContent")
            .addHeader("x-goog-api-key", trimmedKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use mapHttpError(response.code, response.header("Retry-After"), bodyStr)
                }
                parseAudioResponse(bodyStr)
            }
        } catch (_: UnknownHostException) {
            TtsResult.Error.NoInternet
        } catch (_: SocketTimeoutException) {
            TtsResult.Error.ServerError(408, "Timeout")
        } catch (_: IOException) {
            if (!isNetworkAvailable()) TtsResult.Error.NoInternet
            else TtsResult.Error.ServerError(503, "Network interrupted")
        } catch (e: Exception) {
            TtsResult.Error.Unknown(e.localizedMessage ?: "Unexpected error")
        }
    }

    private fun parseAudioResponse(jsonStr: String): TtsResult {
        val root = JSONObject(jsonStr)
        val promptFeedback = root.optJSONObject("promptFeedback")
        val blockReason = promptFeedback?.optString("blockReason").orEmpty()
        if (blockReason.isNotBlank()) {
            return TtsResult.Error.BlockedContent
        }

        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return TtsResult.Error.ServerError(500, "Empty response")
        }

        val candidate = candidates.getJSONObject(0)
        val finishReason = candidate.optString("finishReason").orEmpty()
        if (finishReason == "SAFETY" || finishReason == "PROHIBITED_CONTENT" ||
            finishReason == "BLOCKLIST" || finishReason == "RECITATION"
        ) {
            return TtsResult.Error.BlockedContent
        }

        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: return TtsResult.Error.ServerError(500, "No audio parts")

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inlineData = part.optJSONObject("inlineData") ?: continue
            val base64Data = inlineData.optString("data")
            if (base64Data.isNotBlank()) {
                val mimeType = inlineData.optString("mimeType")
                val sampleRate = parseSampleRate(mimeType)
                val pcm = Base64.decode(base64Data, Base64.DEFAULT)
                if (pcm.isNotEmpty()) {
                    return TtsResult.Success(pcmBytes = pcm, sampleRate = sampleRate)
                }
            }
        }

        return TtsResult.Error.ServerError(500, "Audio data empty")
    }

    private fun parseSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000
    }

    private fun mapHttpError(code: Int, retryAfterHeader: String?, bodyStr: String): TtsResult.Error {
        val apiMsg = parseApiErrorMessage(bodyStr)
        val lower = apiMsg.lowercase()
        return when (code) {
            400 -> {
                if (lower.contains("api key") || lower.contains("api_key_invalid")) {
                    TtsResult.Error.InvalidKey(apiMsg)
                } else if (lower.contains("safety") || lower.contains("blocked")) {
                    TtsResult.Error.BlockedContent
                } else {
                    TtsResult.Error.Unknown(apiMsg.ifBlank { "Invalid request (400)" })
                }
            }
            401, 403 -> TtsResult.Error.InvalidKey(apiMsg)
            429 -> TtsResult.Error.RateLimit(retryAfterHeader?.toLongOrNull())
            in 500..599 -> TtsResult.Error.ServerError(code, apiMsg)
            else -> TtsResult.Error.Unknown(apiMsg.ifBlank { "HTTP $code" })
        }
    }

    private fun parseApiErrorMessage(bodyStr: String): String {
        return try {
            JSONObject(bodyStr).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
