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

class PanelPairingClient {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()

    fun start(baseUrl: String, deviceId: String, name: String, update: (PairingUpdate) -> Unit) {
        post("${baseUrl.trimEnd('/')}/api/nspanel_companion/pair/start", JSONObject()
            .put("device_id", deviceId).put("name", name)) { result ->
            val error = result.optString("error")
            if (error.isNotBlank()) return@post update(PairingUpdate.Error(error))
            val requestId = result.getString("request_id")
            val claim = result.getString("claim")
            update(PairingUpdate.Code(result.getString("code"), result.optInt("expires_in", 300)))
            poll(baseUrl, requestId, claim, update)
        }
    }

    private fun poll(baseUrl: String, requestId: String, claim: String, update: (PairingUpdate) -> Unit) {
        handler.postDelayed({
            post("${baseUrl.trimEnd('/')}/api/nspanel_companion/pair/status", JSONObject()
                .put("request_id", requestId).put("claim", claim)) { result ->
                when (result.optString("status")) {
                    "approved" -> update(PairingUpdate.Approved(PanelCredentials(baseUrl.trimEnd('/'), result.getString("panel_id"), result.getString("token"))))
                    "pending" -> poll(baseUrl, requestId, claim, update)
                    else -> update(PairingUpdate.Error(result.optString("error", "Pairing failed")))
                }
            }
        }, 2_000)
    }

    private fun post(url: String, json: JSONObject, complete: (JSONObject) -> Unit) {
        val request = Request.Builder().url(url).post(json.toString().toRequestBody(JSON)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                handler.post { complete(JSONObject().put("error", error.message)) }
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    runCatching { JSONObject(it.body?.string().orEmpty()) }
                        .getOrElse { JSONObject().put("error", "Invalid server response") }
                }
                handler.post { complete(result) }
            }
        })
    }

    companion object { private val JSON = "application/json".toMediaType() }
}

sealed class PairingUpdate {
    data class Code(val value: String, val expiresIn: Int) : PairingUpdate()
    data class Approved(val credentials: PanelCredentials) : PairingUpdate()
    data class Error(val message: String) : PairingUpdate()
}
