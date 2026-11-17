package com.example.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pulsechat_auth_session"
)

/**
 * Manages "Remember Me" session policy and optional Keystore-encrypted remembered username.
 * Passwords and auth tokens are NEVER stored here.
 */
class AuthSessionStore(private val context: Context) {

    private val appContext = context.applicationContext

    suspend fun isRememberMeEnabled(): Boolean {
        val prefs = appContext.authSessionDataStore.data.first()
        return prefs[KEY_REMEMBER_ME] ?: true
    }

    suspend fun getRememberedUsername(): String {
        val prefs = appContext.authSessionDataStore.data.first()
        val rememberMe = prefs[KEY_REMEMBER_ME] ?: true
        if (!rememberMe) return ""
        val wrapped = prefs[KEY_REMEMBERED_USERNAME].orEmpty()
        if (wrapped.isBlank()) return ""
        return decryptUsername(wrapped)
    }

    suspend fun saveLoginPreference(rememberMe: Boolean, username: String) {
        val normalized = username.trim().lowercase()
        appContext.authSessionDataStore.edit { prefs ->
            prefs[KEY_REMEMBER_ME] = rememberMe
            if (rememberMe && normalized.isNotEmpty()) {
                prefs[KEY_REMEMBERED_USERNAME] = encryptUsername(normalized)
            } else {
                prefs.remove(KEY_REMEMBERED_USERNAME)
            }
        }
    }

    suspend fun clearOnSignOut() {
        clearInProcessSession()
        appContext.authSessionDataStore.edit { prefs ->
            val rememberMe = prefs[KEY_REMEMBER_ME] ?: true
            if (!rememberMe) {
                prefs.remove(KEY_REMEMBERED_USERNAME)
            }
        }
    }

    private fun encryptUsername(username: String): String {
        val key = getOrCreateSessionSecretKey()
        val raw = username.toByteArray(StandardCharsets.UTF_8)
        if (key == null) {
            return "b64:${Base64.encodeToString(raw, Base64.NO_WRAP)}"
        }
        return runCatching {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(raw)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
            "ks:$ivB64:$ctB64"
        }.getOrElse {
            "b64:${Base64.encodeToString(raw, Base64.NO_WRAP)}"
        }
    }

    private fun decryptUsername(wrapped: String): String {
        return when {
            wrapped.startsWith("ks:") -> {
                val parts = wrapped.split(":")
                if (parts.size != 3) return ""
                val key = getOrCreateSessionSecretKey() ?: return ""
                runCatching {
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val ct = Base64.decode(parts[2], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(AES_GCM)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(ct), StandardCharsets.UTF_8)
                }.getOrDefault("")
            }
            wrapped.startsWith("b64:") -> {
                runCatching {
                    String(
                        Base64.decode(wrapped.removePrefix("b64:"), Base64.NO_WRAP),
                        StandardCharsets.UTF_8
                    )
                }.getOrDefault("")
            }
            else -> wrapped
        }
    }

    private fun getOrCreateSessionSecretKey(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey) ?: run {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }
        }.getOrNull()
    }

    companion object {
        private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me_enabled")
        private val KEY_REMEMBERED_USERNAME = stringPreferencesKey("remembered_username_wrapped")
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "pulsechat_auth_session_v1"
        private const val AES_GCM = "AES/GCM/NoPadding"

        @Volatile
        private var inProcessAuthenticated: Boolean = false

        fun isSessionActiveInProcess(): Boolean = inProcessAuthenticated

        fun markSessionActiveInProcess() {
            inProcessAuthenticated = true
        }

        fun clearInProcessSession() {
            inProcessAuthenticated = false
        }
    }
}
