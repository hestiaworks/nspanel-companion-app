package dev.hacompanion.panel.ui.model

import org.json.JSONObject

/** A panel that can be called, and whether it is free to answer. */
data class IntercomPeer(val panelId: String, val name: String, val busy: Boolean)

/**
 * Where a call has got to.
 *
 * The panel holds this; Home Assistant holds only who is in a call with
 * whom, which is the least it can know and still route a signal.
 */
enum class CallPhase { IDLE, CALLING, RINGING, CONNECTING, CONNECTED }

/**
 * The roster, in the order it arrived.
 *
 * Home Assistant has already decided who belongs on it — itself excluded,
 * intercom off excluded, offline excluded — so there is nothing to filter
 * here and no second opinion to keep in step.
 */
fun parseRoster(json: JSONObject): List<IntercomPeer> {
    val values = json.optJSONArray("panels") ?: return emptyList()
    return buildList {
        for (index in 0 until values.length()) {
            val item = values.optJSONObject(index) ?: continue
            val id = item.optString("panel_id").takeIf(String::isNotBlank) ?: continue
            add(
                IntercomPeer(
                    panelId = id,
                    name = item.optString("name").takeIf(String::isNotBlank) ?: id,
                    busy = item.optBoolean("busy", false),
                ),
            )
        }
    }
}
