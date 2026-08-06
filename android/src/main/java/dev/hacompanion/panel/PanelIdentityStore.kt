package dev.hacompanion.panel

import android.content.Context
import java.util.UUID

class PanelIdentityStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null) ?: PanelIdentity.newDeviceId().also {
            check(preferences.edit().putString(KEY_DEVICE_ID, it).commit()) {
                "Unable to persist panel identity"
            }
        }

    companion object {
        private const val PREFERENCES = "panel_identity"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

object PanelIdentity {
    private val FORMAT = Regex("panel-[0-9a-f]{32}")

    fun newDeviceId(uuid: UUID = UUID.randomUUID()): String =
        "panel-${uuid.toString().replace("-", "").lowercase()}"

    fun isValid(deviceId: String): Boolean = FORMAT.matches(deviceId)
}
