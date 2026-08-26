package dev.hacompanion.panel

import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Asks the bridge for a stream URL shortly before the camera page needs it.
 *
 * Measured on the panel, that request is about 620 ms of the 1.7 s it takes to
 * reach a first frame, because Scrypted mints a session per request rather than
 * handing back something it already has. Warming moves that cost to a moment
 * when nothing is waiting on it.
 *
 * It is only ever an optimisation: a URL that is missing, stale or refused
 * leaves the page resolving exactly as it did before.
 */
class CameraStreamWarmer(
    private val cache: CameraStreamCache = CameraStreamCache(),
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun warm(widgets: List<DashboardWidget>) {
        widgets.forEach { widget ->
            val endpoint = widget.talkbackUrl?.takeIf(String::isNotBlank) ?: return@forEach
            val key = widget.talkbackKey?.takeIf(String::isNotBlank) ?: return@forEach
            val entry = cacheKey(widget)
            if (!cache.beginPrefetch(entry, now())) return@forEach
            Thread {
                val url = fetchStreamUrl(client, endpoint, key)
                cache.store(entry, url, now())
                if (url.isNotBlank()) Log.i(TAG, "warmed ${widget.streamName ?: "camera"}")
            }.start()
        }
    }

    /** A warmed URL for this camera, if one is fresh. Consumes it. */
    fun claim(widget: DashboardWidget): String? = cache.take(cacheKey(widget), now())

    private fun cacheKey(widget: DashboardWidget) =
        "${widget.talkbackUrl}#${widget.streamName ?: ""}"

    private companion object {
        const val TAG = "NSPanelCamera"
    }
}

/** The bridge request both the warmer and the camera page make. Blocking. */
internal fun fetchStreamUrl(client: OkHttpClient, endpoint: String, key: String): String =
    runCatching {
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use ""
            JSONObject(response.body?.string().orEmpty()).optString("video_url")
        }
    }.getOrElse {
        Log.w("NSPanelCamera", "Could not resolve a current stream URL: ${it.message}")
        ""
    }
