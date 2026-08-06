package dev.hacompanion.panel

import android.content.Context

data class PanelCredentials(val baseUrl: String, val panelId: String, val token: String)

class PanelProvisioningStore(context: Context) {
    private val preferences = context.getSharedPreferences("panel_provisioning", Context.MODE_PRIVATE)
    private val cipher = KeystoreSecretCipher("ha_companion_panel_token")

    fun save(value: PanelCredentials) {
        require(value.baseUrl.startsWith("http://") || value.baseUrl.startsWith("https://"))
        require(PanelIdentity.isValid(value.panelId))
        require(value.token.length >= 32)
        val encrypted = cipher.encrypt(value.token)
        preferences.edit().putString("url", value.baseUrl.trimEnd('/'))
            .putString("panel_id", value.panelId).putString("token", encrypted.first)
            .putString("token_iv", encrypted.second).apply()
    }

    fun load(): PanelCredentials? {
        val url = preferences.getString("url", null) ?: return null
        val panelId = preferences.getString("panel_id", null) ?: return null
        val token = preferences.getString("token", null) ?: return null
        val iv = preferences.getString("token_iv", null) ?: return null
        return try {
            PanelCredentials(url, panelId, cipher.decrypt(token, iv))
        } catch (_: Exception) { null }
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Unable to clear panel pairing" }
    }
}
