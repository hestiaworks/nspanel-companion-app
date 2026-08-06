package dev.hacompanion.panel

import android.content.Context

class SecureSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("ha_connection", Context.MODE_PRIVATE)
    private val cipher = KeystoreSecretCipher("ha_companion_connection")

    fun load(): ConnectionSettings? {
        val url = preferences.getString(KEY_URL, null) ?: return null
        val encrypted = preferences.getString(KEY_TOKEN, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return try {
            ConnectionSettings(url, cipher.decrypt(encrypted, iv))
        } catch (_: Exception) {
            null
        }
    }

    fun save(settings: ConnectionSettings) {
        settings.validate()
        val encrypted = cipher.encrypt(settings.accessToken)
        preferences.edit()
            .putString(KEY_URL, settings.baseUrl.trim().trimEnd('/'))
            .putString(KEY_TOKEN, encrypted.first)
            .putString(KEY_IV, encrypted.second)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_TOKEN = "token"
        private const val KEY_IV = "token_iv"
    }
}
