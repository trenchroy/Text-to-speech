package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.arLabsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ar_labs_prefs")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class UserSettings(
    val apiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val selectedVoiceId: String = "deep_male",
    val nonStopEnabled: Boolean = true,
    val repeatEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackVolume: Float = 1.0f
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_gemini_api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val NON_STOP_ENABLED = booleanPreferencesKey("non_stop_enabled")
        val REPEAT_ENABLED = booleanPreferencesKey("repeat_enabled")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_VOLUME = floatPreferencesKey("playback_volume")
    }

    val settingsFlow: Flow<UserSettings> = context.arLabsDataStore.data.map { prefs ->
        val encrypted = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        val decryptedKey = if (encrypted.isNotBlank()) KeyStoreCrypto.decrypt(encrypted) else ""
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        UserSettings(
            apiKey = decryptedKey,
            themeMode = theme,
            selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID] ?: "deep_male",
            nonStopEnabled = prefs[Keys.NON_STOP_ENABLED] ?: true,
            repeatEnabled = prefs[Keys.REPEAT_ENABLED] ?: false,
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.75f, 1.5f),
            playbackVolume = (prefs[Keys.PLAYBACK_VOLUME] ?: 1.0f).coerceIn(0.0f, 1.0f)
        )
    }

    suspend fun getApiKey(): String = settingsFlow.first().apiKey

    suspend fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        context.arLabsDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.ENCRYPTED_API_KEY)
            } else {
                prefs[Keys.ENCRYPTED_API_KEY] = KeyStoreCrypto.encrypt(trimmed)
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.arLabsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSelectedVoiceId(voiceId: String) {
        context.arLabsDataStore.edit { it[Keys.SELECTED_VOICE_ID] = voiceId }
    }

    suspend fun setNonStopEnabled(enabled: Boolean) {
        context.arLabsDataStore.edit { it[Keys.NON_STOP_ENABLED] = enabled }
    }

    suspend fun setRepeatEnabled(enabled: Boolean) {
        context.arLabsDataStore.edit { it[Keys.REPEAT_ENABLED] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.arLabsDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.75f, 1.5f) }
    }

    suspend fun setPlaybackVolume(volume: Float) {
        context.arLabsDataStore.edit { it[Keys.PLAYBACK_VOLUME] = volume.coerceIn(0.0f, 1.0f) }
    }
}

private object KeyStoreCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ar_labs_gemini_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX_ENC = "enc_v1:"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            PREFIX_ENC + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // Fallback base64 obfuscation only if device KeyStore hardware is unavailable
            "plain:" + Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(stored: String): String {
        return try {
            if (stored.startsWith("plain:")) {
                val raw = Base64.decode(stored.removePrefix("plain:"), Base64.NO_WRAP)
                return String(raw, StandardCharsets.UTF_8)
            }
            if (!stored.startsWith(PREFIX_ENC)) return ""
            val combined = Base64.decode(stored.removePrefix(PREFIX_ENC), Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            if (ivLen <= 0 || combined.size <= 1 + ivLen) return ""
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
