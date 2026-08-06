package dev.hacompanion.panel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors

class PanelPairingAdvertiser(
    context: Context,
    private val deviceId: String,
    private val displayName: String,
    private val onPairRequested: (String) -> Unit,
) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val executor = Executors.newSingleThreadExecutor()
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        if (server != null) return
        val socket = ServerSocket(0)
        server = socket
        val info = NsdServiceInfo().apply {
            serviceName = displayName
            serviceType = SERVICE_TYPE
            port = socket.localPort
            setAttribute("id", deviceId)
            setAttribute("version", BuildConfig.VERSION_NAME)
        }
        registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }.also { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, it) }
        executor.execute {
            while (!socket.isClosed) {
                try { handle(socket.accept()) } catch (_: Exception) { if (!socket.isClosed) continue }
            }
        }
    }

    private fun handle(client: java.net.Socket) = client.use { socket ->
        socket.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = reader.readLine().orEmpty()
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", true)) contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val body = CharArray(contentLength.coerceAtMost(8_192))
        var read = 0
        while (read < body.size) {
            val count = reader.read(body, read, body.size - read)
            if (count < 0) break
            read += count
        }
        val baseUrl = runCatching { JSONObject(String(body, 0, read)).getString("ha_url") }.getOrDefault("")
        val accepted = requestLine.startsWith("POST /pair ") && baseUrl.startsWith("http")
        if (accepted) onPairRequested(baseUrl)
        val payload = JSONObject().put("accepted", accepted).put("device_id", deviceId).toString()
        val status = if (accepted) "200 OK" else "400 Bad Request"
        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${payload.toByteArray().size}\r\nConnection: close\r\n\r\n$payload".toByteArray())
    }

    fun stop() {
        registration?.let { runCatching { manager.unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        server = null
        executor.shutdownNow()
    }

    companion object { private const val SERVICE_TYPE = "_nspanel-companion._tcp." }
}
