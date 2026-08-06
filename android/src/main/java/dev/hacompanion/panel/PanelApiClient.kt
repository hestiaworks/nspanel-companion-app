package dev.hacompanion.panel

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PanelApiClient(
    private val credentials: PanelCredentials,
    private val onStatus: (ConnectionStatus) -> Unit,
    private val onInitialStates: (List<EntityState>) -> Unit,
    private val onEntityChanged: (EntityState) -> Unit,
    private val onDoorbellEvent: (DoorbellEvent) -> Unit,
    private val onWeatherForecast: (String, String, org.json.JSONArray) -> Unit = { _, _, _ -> },
) {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).build()
    private val ids = AtomicInteger(1)
    private var socket: WebSocket? = null
    private var stopped = true
    private var retrySeconds = 1

    fun start() { stopped = false; connect() }
    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        socket?.close(1000, "Panel client stopped")
        socket = null
        client.dispatcher.executorService.shutdown()
    }

    fun callService(domain: String, service: String, entityId: String, data: JSONObject): Boolean =
        socket?.send(JSONObject().put("type", "call_service").put("id", ids.getAndIncrement())
            .put("domain", domain).put("service", service).put("entity_id", entityId)
            .put("service_data", data).toString()) == true

    private fun connect() {
        if (stopped) return
        onStatus(ConnectionStatus(ConnectionPhase.CONNECTING, "Connecting with paired panel credentials"))
        val http = credentials.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/nspanel_companion/panel/ws")
            .addQueryParameter("panel_id", credentials.panelId).build()
        socket = client.newWebSocket(Request.Builder().url(http)
            .header("Authorization", "Bearer ${credentials.token}").build(), Listener())
    }

    private fun retry(reason: String) {
        if (stopped) return
        val delay = retrySeconds
        retrySeconds = (retrySeconds * 2).coerceAtMost(30)
        onStatus(ConnectionStatus(ConnectionPhase.RETRYING, "$reason. Retrying in ${delay}s"))
        handler.postDelayed(::connect, delay * 1_000L)
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            retrySeconds = 1
            handler.post { onStatus(ConnectionStatus(ConnectionPhase.ONLINE, "Connected with panel-scoped authorization")) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (message.optString("type")) {
                "initial_states" -> {
                    val values = message.optJSONArray("states") ?: return
                    val states = buildList { for (index in 0 until values.length()) parseState(values.optJSONObject(index))?.let(::add) }
                    handler.post { onInitialStates(states) }
                }
                "state_changed" -> parseState(message.optJSONObject("state"))?.let { handler.post { onEntityChanged(it) } }
                "weather_forecast" -> {
                    val entityId = message.optString("entity_id")
                    val forecastType = message.optString("forecast_type")
                    val forecast = message.optJSONArray("forecast") ?: return
                    if (entityId.startsWith("weather.") && forecastType in setOf("daily", "hourly")) {
                        handler.post { onWeatherForecast(entityId, forecastType, forecast) }
                    }
                }
                "doorbell" -> message.optJSONObject("data")?.let { data -> handler.post { onDoorbellEvent(DoorbellEvent(
                    streamBaseUrl = data.optString("stream_base_url").takeIf(String::isNotBlank),
                    streamName = data.optString("stream_name").takeIf(String::isNotBlank),
                    talkbackUrl = data.optString("talkback_url").takeIf(String::isNotBlank),
                    talkbackKey = data.optString("talkback_key").takeIf(String::isNotBlank),
                    quietMode = data.optBoolean("quiet_mode"),
                    autoCloseMs = data.optLong("auto_close_ms").takeIf { it > 0 },
                    talkExtendMs = data.optLong("talk_extend_ms", 15_000L).coerceIn(0L, 60_000L),
                    talkbackTestUrl = data.optString("talkback_test_url").takeIf(String::isNotBlank),
                )) } }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            if (response?.code == 401) {
                stopped = true
                handler.post { onStatus(ConnectionStatus(ConnectionPhase.AUTH_FAILED, "Panel authorization was revoked")) }
            } else handler.post { retry(t.message?.take(80) ?: "Connection failed") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            handler.post { retry("Connection closed") }
        }
    }

    private fun parseState(value: JSONObject?): EntityState? {
        value ?: return null
        val entityId = value.optString("entity_id")
        if (!entityId.contains('.')) return null
        return EntityState(entityId, value.optString("state"), value.optJSONObject("attributes") ?: JSONObject())
    }
}
