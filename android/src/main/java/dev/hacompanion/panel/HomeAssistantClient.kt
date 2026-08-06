package dev.hacompanion.panel

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

class HomeAssistantClient(
    private val settings: ConnectionSettings,
    private val onStatus: (ConnectionStatus) -> Unit,
    private val onInitialStates: (List<EntityState>) -> Unit = {},
    private val onEntityChanged: (EntityState) -> Unit = {},
    private val onDoorbellEvent: (DoorbellEvent) -> Unit = {},
    private val onDashboardLayout: (DashboardLayout) -> Unit = {},
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var stopped = true
    private var socket: WebSocket? = null
    private var retry: ScheduledFuture<*>? = null
    private var attempt = 0
    private val nextCommandId = AtomicInteger(10)
    @Volatile private var online = false

    fun start() {
        stopped = false
        attempt = 0
        connect()
    }

    fun stop() {
        stopped = true
        online = false
        retry?.cancel(false)
        retry = null
        socket?.close(1000, "Client stopped")
        socket = null
        scheduler.shutdownNow()
        httpClient.dispatcher.executorService.shutdown()
        emit(ConnectionPhase.STOPPED, "Connection stopped")
    }

    fun callService(
        domain: String,
        service: String,
        entityId: String,
        serviceData: JSONObject = JSONObject(),
    ): Boolean {
        if (!online) return false
        return socket?.send(
            HomeAssistantProtocol.callService(
                nextCommandId.getAndIncrement(),
                domain,
                service,
                entityId,
                serviceData,
            ),
        ) == true
    }

    private fun connect() {
        if (stopped) return
        emit(ConnectionPhase.CONNECTING, "Connecting to Home Assistant")
        val request = try {
            Request.Builder().url(settings.websocketUrl()).build()
        } catch (error: IllegalArgumentException) {
            emit(ConnectionPhase.AUTH_FAILED, error.message ?: "Invalid Home Assistant URL")
            return
        }
        socket = httpClient.newWebSocket(request, Listener())
    }

    private fun scheduleRetry(reason: String) {
        if (stopped || retry?.isDone == false) return
        val delay = retryPolicy.delayForAttempt(attempt++)
        emit(ConnectionPhase.RETRYING, "$reason. Retrying in ${delay / 1_000}s")
        retry = scheduler.schedule(
            {
                retry = null
                connect()
            },
            delay,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun emit(phase: ConnectionPhase, detail: String) {
        mainHandler.post { onStatus(ConnectionStatus(phase, detail)) }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            emit(ConnectionPhase.AUTHENTICATING, "Socket connected; waiting for authentication")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (HomeAssistantProtocol.messageType(text)) {
                "auth_required" -> {
                    emit(ConnectionPhase.AUTHENTICATING, "Authenticating")
                    webSocket.send(HomeAssistantProtocol.auth(settings.accessToken))
                }
                "auth_ok" -> {
                    attempt = 0
                    online = true
                    emit(
                        ConnectionPhase.ONLINE,
                        "Connected to Home Assistant " +
                            HomeAssistantProtocol.detail(text, "ha_version"),
                    )
                    webSocket.send(HomeAssistantProtocol.subscribeToStateChanges())
                    webSocket.send(HomeAssistantProtocol.getStates())
                    webSocket.send(HomeAssistantProtocol.subscribeToEvent(DOORBELL_EVENT))
                    webSocket.send(HomeAssistantProtocol.subscribeToEvent(LAYOUT_EVENT, 4))
                }
                "auth_invalid" -> {
                    stopped = true
                    online = false
                    emit(
                        ConnectionPhase.AUTH_FAILED,
                        HomeAssistantProtocol.detail(text, "message")
                            .ifBlank { "Authentication failed" },
                    )
                    webSocket.close(1000, "Authentication failed")
                }
                "result" -> {
                    val states = HomeAssistantProtocol.statesFromResult(text)
                    if (states.isNotEmpty()) mainHandler.post { onInitialStates(states) }
                }
                "event" -> {
                    HomeAssistantProtocol.doorbellEvent(text, DOORBELL_EVENT)?.let { event ->
                        mainHandler.post { onDoorbellEvent(event) }
                    }
                    HomeAssistantProtocol.dashboardLayoutEvent(text, LAYOUT_EVENT)?.let { layout ->
                        mainHandler.post { onDashboardLayout(layout) }
                    }
                    HomeAssistantProtocol.changedEntity(text)?.let { state ->
                        mainHandler.post { onEntityChanged(state) }
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            online = false
            socket = null
            if (!stopped) scheduleRetry("Connection closed")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            online = false
            socket = null
            scheduleRetry(t.message?.take(80) ?: "Connection failed")
        }
    }

    companion object {
        const val DOORBELL_EVENT = "nspanel_doorbell"
        const val LAYOUT_EVENT = "nspanel_layout"
    }
}
