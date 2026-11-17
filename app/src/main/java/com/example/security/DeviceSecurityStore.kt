package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pulsechat_device_security"
)

class DeviceSecurityStore(private val context: Context) {

    private val appContext = context.applicationContext

    suspend fun getOrCreateDeviceId(): String {
        val current = appContext.securityDataStore.data.map { prefs -> prefs[KEY_DEVICE_ID] }.first()
        if (!current.isNullOrBlank()) return current
        val created = "dev_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        appContext.securityDataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = created
        }
        return created
    }

    suspend fun loadE2eeMaterial(userId: String): LocalE2eeKeyMaterial? {
        val key = stringPreferencesKey("e2ee_material_$userId")
        val wrapped = appContext.securityDataStore.data.map { prefs -> prefs[key] }.first()
            ?: return null
        return E2eeCryptoManager.unwrapPrivateMaterialFromStorage(wrapped)
    }

    /**
     * Loads the active key material and all prior wrapped key versions retained on this device
     * keyed by keyFingerprint so key rotation never breaks decryption of older messages.
     */
    suspend fun loadAllE2eeMaterials(userId: String): Map<String, LocalE2eeKeyMaterial> {
        val activeKey = stringPreferencesKey("e2ee_material_$userId")
        val historyKey = stringPreferencesKey("e2ee_material_history_$userId")
        val prefs = appContext.securityDataStore.data.first()
        val result = linkedMapOf<String, LocalE2eeKeyMaterial>()

        val historyRaw = prefs[historyKey].orEmpty()
        if (historyRaw.isNotBlank()) {
            runCatching {
                val arr = JSONArray(historyRaw)
                for (i in 0 until arr.length()) {
                    val wrapped = arr.optString(i)
                    val mat = E2eeCryptoManager.unwrapPrivateMaterialFromStorage(wrapped)
                    if (mat != null) {
                        result[mat.keyFingerprint] = mat
                    }
                }
            }
        }

        prefs[activeKey]?.let { wrapped ->
            E2eeCryptoManager.unwrapPrivateMaterialFromStorage(wrapped)?.let { active ->
                result[active.keyFingerprint] = active
            }
        }
        return result
    }

    suspend fun saveE2eeMaterial(material: LocalE2eeKeyMaterial) {
        val activeKey = stringPreferencesKey("e2ee_material_${material.userId}")
        val historyKey = stringPreferencesKey("e2ee_material_history_${material.userId}")
        val wrapped = E2eeCryptoManager.wrapPrivateMaterialForStorage(material)
        appContext.securityDataStore.edit { prefs ->
            prefs[activeKey] = wrapped

            val existingRaw = prefs[historyKey].orEmpty()
            val retainedWrapped = mutableListOf<String>()
            val seenFingerprints = mutableSetOf(material.keyFingerprint)
            retainedWrapped.add(wrapped)

            if (existingRaw.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(existingRaw)
                    for (i in 0 until arr.length()) {
                        val itemWrapped = arr.optString(i)
                        val mat = E2eeCryptoManager.unwrapPrivateMaterialFromStorage(itemWrapped)
                        if (mat != null && seenFingerprints.add(mat.keyFingerprint)) {
                            retainedWrapped.add(itemWrapped)
                        }
                    }
                }
            }

            val bounded = JSONArray()
            retainedWrapped.take(MAX_RETAINED_KEY_VERSIONS).forEach { bounded.put(it) }
            prefs[historyKey] = bounded.toString()
        }
    }

    suspend fun clearE2eeMaterial(userId: String) {
        val activeKey = stringPreferencesKey("e2ee_material_$userId")
        val historyKey = stringPreferencesKey("e2ee_material_history_$userId")
        appContext.securityDataStore.edit { prefs ->
            prefs.remove(activeKey)
            prefs.remove(historyKey)
        }
    }

    companion object {
        private val KEY_DEVICE_ID = stringPreferencesKey("device_installation_id")
        private const val MAX_RETAINED_KEY_VERSIONS = 20
    }
}
