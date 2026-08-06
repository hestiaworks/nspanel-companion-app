package dev.hacompanion.panel

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

data class DiscoveredHomeAssistant(val name: String, val baseUrl: String)

class HomeAssistantDiscovery(context: Context, private val found: (DiscoveredHomeAssistant) -> Unit) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var active = false
    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) { active = true }
        override fun onDiscoveryStopped(type: String) { active = false }
        override fun onStartDiscoveryFailed(type: String, code: Int) { active = false }
        override fun onStopDiscoveryFailed(type: String, code: Int) { active = false }
        override fun onServiceLost(service: NsdServiceInfo) = Unit
        override fun onServiceFound(service: NsdServiceInfo) {
            if (!service.serviceType.startsWith("_home-assistant._tcp")) return
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                override fun onServiceResolved(info: NsdServiceInfo) {
                    fun property(name: String): String? = info.attributes[name]
                        ?.toString(Charsets.UTF_8)?.trim()?.takeIf(String::isNotEmpty)
                    val address = info.host?.hostAddress ?: return
                    val host = if (address.contains(':')) "[$address]" else address
                    val url = property("internal_url") ?: property("base_url") ?: "http://$host:${info.port}"
                    found(DiscoveredHomeAssistant(property("location_name") ?: info.serviceName, url))
                }
            })
        }
    }

    fun start() = manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    fun stop() {
        if (active) try { manager.stopServiceDiscovery(listener) } catch (_: IllegalArgumentException) { }
        active = false
    }

    companion object { const val SERVICE_TYPE = "_home-assistant._tcp." }
}
