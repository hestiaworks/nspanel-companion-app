package dev.hacompanion.panel

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PanelSyncClient(
    private val credentials: PanelCredentials,
    private val currentRevision: () -> String,
    private val diagnostics: () -> String = { "" },
    private val onLayout: (DashboardLayout) -> Unit,
    private val onPanelIdentity: (String) -> Unit = {},
    private val onAuthenticationFailed: () -> Unit = {},
    private val onHealth: (String) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
    private var stopped = true
    private val sync = Runnable { synchronize() }

    fun start() { stopped = false; handler.post(sync) }
    fun stop() { stopped = true; handler.removeCallbacks(sync); client.dispatcher.cancelAll() }

    private fun synchronize() {
        if (stopped) return
        val body = JSONObject().put("panel_id", credentials.panelId)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("layout_revision", currentRevision())
            .put("diagnostics", diagnostics().take(16_384))
        val request = Request.Builder()
            .url("${credentials.baseUrl}/api/nspanel_companion/panel/sync")
            .header("Authorization", "Bearer ${credentials.token}")
            .post(body.toString().toRequestBody(JSON)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onHealth("Heartbeat failed: ${e.javaClass.simpleName}")
                schedule(15)
            }
            override fun onResponse(call: Call, response: Response) {
                val status = response.code
                val result = response.use { runCatching { JSONObject(it.body?.string().orEmpty()) }.getOrNull() }
                handler.post {
                    if (stopped) return@post
                    if (status == 401) { stopped = true; onAuthenticationFailed(); return@post }
                    if (status in 200..299) onHealth("Heartbeat online")
                    else onHealth("Heartbeat HTTP $status")
                    result?.optString("panel_name")?.takeIf(String::isNotBlank)?.let(onPanelIdentity)
                    result?.optJSONObject("layout")?.let { layout ->
                        runCatching { DashboardLayout.parse(layout) }.onSuccess(onLayout)
                    }
                    schedule(result?.optInt("heartbeat_seconds", 15) ?: 15)
                }
            }
        })
    }

    private fun schedule(seconds: Int) {
        handler.post {
            if (!stopped) handler.postDelayed(sync, seconds.coerceIn(10, 60) * 1_000L)
        }
    }

    companion object { private val JSON = "application/json".toMediaType() }
}
