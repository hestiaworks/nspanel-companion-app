package dev.hacompanion.panel

import android.content.Context
import android.net.Uri
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.VideoSink
import org.webrtc.audio.JavaAudioDeviceModule

enum class DoorbellSessionPhase {
    CONNECTING,
    LIVE,
    RETRYING,
    FAILED,
    STOPPED,
}

data class DoorbellSessionStatus(
    val phase: DoorbellSessionPhase,
    val detail: String,
)

data class DoorbellDiagnostics(
    val width: Int,
    val height: Int,
    val framesPerSecond: Double,
    val codec: String,
    val decoder: String,
    val connectionTimeMs: Long?,
    val reconnectCount: Int,
    val memoryPssMb: Int,
)

/**
 * Native go2rtc WebRTC client with bounded reconnects. The factory, EGL context,
 * renderer, and microphone source live for the activity lifetime; retries only
 * replace the lightweight PeerConnection and signaling WebSocket.
 */
class NativeDoorbellSession(
    context: Context,
    private val renderer: SurfaceViewRenderer,
    streamBaseUrl: String,
    streamName: String,
    private val microphoneAllowed: Boolean,
    private val quietMode: Boolean,
    private val talkbackTestUrl: String? = null,
    private val onStatus: (DoorbellSessionStatus) -> Unit,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val eglBase = EglBase.create()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    private val signalingUrl = buildSignalingUrl(streamBaseUrl, streamName)
    private val peerFactory: PeerConnectionFactory
    private val audioSource: AudioSource?
    private val microphoneTrack: AudioTrack?

    private var webSocket: WebSocket? = null
    private var peerConnection: PeerConnection? = null
    private var remoteVideoTrack: VideoTrack? = null
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionReady = false
    private var generation = 0
    private var retryAttempt = 0
    private var stopped = false
    private var live = false
    @Volatile private var talking = false
    private val talkbackRecorder = TalkbackTestRecorder(
        uploadUrl = talkbackTestUrl,
        httpClient = httpClient,
    )
    private var initialConnectStartedAt = 0L
    private var connectedAt: Long? = null
    private var reconnectCount = 0
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var framesPerSecond = 0.0
    private var frameSampleStartedAt = SystemClock.elapsedRealtime()
    private var frameSampleCount = 0
    private val renderingSink = VideoSink { frame ->
        renderer.onFrame(frame)
        videoWidth = frame.rotatedWidth
        videoHeight = frame.rotatedHeight
        frameSampleCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - frameSampleStartedAt
        if (elapsed >= 1_000L) {
            framesPerSecond = frameSampleCount * 1_000.0 / elapsed
            frameSampleCount = 0
            frameSampleStartedAt = now
        }
    }

    private val connectionTimeout = Runnable {
        failCurrentConnection(generation, "Connection timed out")
    }
    private var retryRunnable: Runnable? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions(),
        )
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(false)

        val localMicTest = talkbackTestUrl != null && BuildConfig.DEBUG
        val audioDeviceBuilder = JavaAudioDeviceModule.builder(appContext)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setUseHardwareAcousticEchoCanceler(!localMicTest)
            .setUseHardwareNoiseSuppressor(!localMicTest)
            .setSamplesReadyCallback { samples -> talkbackRecorder.onSamples(samples, talking) }
        if (localMicTest) {
            audioDeviceBuilder
                .setInputSampleRate(48_000)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        val audioDeviceModule = audioDeviceBuilder.createAudioDeviceModule()
        peerFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        audioDeviceModule.release()

        if (microphoneAllowed) {
            audioSource = peerFactory.createAudioSource(MediaConstraints())
            microphoneTrack = peerFactory.createAudioTrack(
                "doorbell_microphone",
                audioSource,
            ).apply { setEnabled(false) }
        } else {
            audioSource = null
            microphoneTrack = null
        }
    }

    fun start() {
        if (stopped) return
        retryAttempt = 0
        reconnectCount = 0
        initialConnectStartedAt = SystemClock.elapsedRealtime()
        connect()
    }

    fun retryNow() {
        if (stopped) return
        retryAttempt = 0
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        connect()
    }

    fun setTalkEnabled(enabled: Boolean): Boolean {
        val track = microphoneTrack ?: return false
        if (!live && enabled) return false
        talking = enabled
        if (enabled) talkbackRecorder.start() else talkbackRecorder.finishAndUpload()
        track.setEnabled(enabled)
        if (live) {
            emit(
                DoorbellSessionPhase.LIVE,
                if (enabled) "Live · talking · native" else
                    "Live · microphone muted · native",
            )
        }
        return true
    }

    fun requestDiagnostics(callback: (DoorbellDiagnostics) -> Unit) {
        val peer = peerConnection
        if (peer == null) {
            callback(createDiagnostics("unknown", "not connected"))
            return
        }
        peer.getStats { report ->
            var codec = "H.264"
            var decoder = "WebRTC platform"
            report.statsMap.values.forEach { stats ->
                if (stats.type == "codec") {
                    val mimeType = stats.members["mimeType"] as? String
                    if (mimeType?.startsWith("video/", ignoreCase = true) == true) {
                        codec = mimeType.substringAfter('/').uppercase()
                    }
                }
                if (stats.type == "inbound-rtp") {
                    (stats.members["decoderImplementation"] as? String)
                        ?.takeIf(String::isNotBlank)
                        ?.let { decoder = it }
                }
            }
            callback(createDiagnostics(codec, decoder))
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        generation++
        mainHandler.removeCallbacks(connectionTimeout)
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        talking = false
        talkbackRecorder.close()
        microphoneTrack?.setEnabled(false)
        closeConnection("activity closed")
        microphoneTrack?.dispose()
        audioSource?.dispose()
        peerFactory.dispose()
        renderer.release()
        eglBase.release()
        httpClient.dispatcher.executorService.shutdown()
        emit(DoorbellSessionPhase.STOPPED, "Doorbell stopped")
    }

    private fun connect() {
        if (stopped) return
        generation++
        val currentGeneration = generation
        closeConnection("reconnecting")
        live = false
        connectedAt = null
        talking = false
        microphoneTrack?.setEnabled(false)
        remoteDescriptionReady = false
        pendingRemoteCandidates.clear()
        emit(DoorbellSessionPhase.CONNECTING, "Connecting · native WebRTC")

        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = peerFactory.createPeerConnection(
            configuration,
            createPeerObserver(currentGeneration),
        )
        if (peer == null) {
            failCurrentConnection(currentGeneration, "Unable to create WebRTC connection")
            return
        }
        peerConnection = peer
        val microphone = microphoneTrack
        if (microphone != null) {
            peer.addTransceiver(
                microphone,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_RECV,
                ),
            )
        } else {
            peer.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
                ),
            )
        }
        peer.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,
            ),
        )
        webSocket = httpClient.newWebSocket(
            Request.Builder().url(signalingUrl).build(),
            createSignalingListener(currentGeneration, peer),
        )
        mainHandler.removeCallbacks(connectionTimeout)
        mainHandler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MS)
    }

    private fun failCurrentConnection(failedGeneration: Int, reason: String) {
        if (stopped || failedGeneration != generation || live) return
        Log.w(TAG, "Connection attempt failed: $reason")
        generation++
        mainHandler.removeCallbacks(connectionTimeout)
        closeConnection("connection failed")
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            emit(DoorbellSessionPhase.FAILED, "Doorbell unavailable · $reason")
            return
        }
        val delay = retryPolicy.delayForAttempt(retryAttempt++)
        reconnectCount++
        emit(
            DoorbellSessionPhase.RETRYING,
            "Reconnecting in ${delay / 1_000}s · $reason",
        )
        retryRunnable = Runnable {
            retryRunnable = null
            connect()
        }.also { mainHandler.postDelayed(it, delay) }
    }

    private fun closeConnection(reason: String) {
        remoteVideoTrack?.removeSink(renderingSink)
        remoteVideoTrack = null
        webSocket?.close(1000, reason)
        webSocket = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    private fun createSignalingListener(
        listenerGeneration: Int,
        peer: PeerConnection,
    ): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent(listenerGeneration)) return
            Log.i(TAG, "go2rtc signaling connected")
            peer.createOffer(
                object : SimpleSdpObserver(listenerGeneration) {
                    override fun onCreateSuccess(description: SessionDescription) {
                        if (!isCurrent(listenerGeneration)) return
                        peer.setLocalDescription(
                            object : SimpleSdpObserver(listenerGeneration) {
                                override fun onSetSuccess() {
                                    if (!isCurrent(listenerGeneration)) return
                                    sendSignal("webrtc/offer", description.description)
                                }
                            },
                            description,
                        )
                    }
                },
                MediaConstraints(),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(listenerGeneration)) return
            val message = try {
                JSONObject(text)
            } catch (error: Exception) {
                mainHandler.post {
                    failCurrentConnection(listenerGeneration, "Invalid signaling response")
                }
                return
            }
            when (message.optString("type")) {
                "webrtc/answer" -> peer.setRemoteDescription(
                    object : SimpleSdpObserver(listenerGeneration) {
                        override fun onSetSuccess() {
                            if (!isCurrent(listenerGeneration)) return
                            remoteDescriptionReady = true
                            pendingRemoteCandidates.forEach(peer::addIceCandidate)
                            pendingRemoteCandidates.clear()
                        }
                    },
                    SessionDescription(
                        SessionDescription.Type.ANSWER,
                        message.getString("value"),
                    ),
                )
                "webrtc/candidate" -> {
                    val candidate = IceCandidate("0", 0, message.getString("value"))
                    if (remoteDescriptionReady) {
                        peer.addIceCandidate(candidate)
                    } else {
                        pendingRemoteCandidates += candidate
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            mainHandler.post {
                failCurrentConnection(listenerGeneration, "Signaling closed")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "go2rtc signaling failed", t)
            mainHandler.post {
                failCurrentConnection(
                    listenerGeneration,
                    t.message?.take(80) ?: "Signaling failed",
                )
            }
        }
    }

    private fun createPeerObserver(observerGeneration: Int) =
        object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE state: $state")
                mainHandler.post {
                    if (!isCurrent(observerGeneration)) return@post
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED,
                        -> {
                            live = true
                            connectedAt = SystemClock.elapsedRealtime()
                            retryAttempt = 0
                            mainHandler.removeCallbacks(connectionTimeout)
                            emit(
                                DoorbellSessionPhase.LIVE,
                                if (microphoneAllowed) {
                                    if (quietMode) {
                                        "Live · quiet · microphone muted"
                                    } else {
                                        "Live · microphone muted · native"
                                    }
                                } else {
                                    "Live · microphone permission required"
                                },
                            )
                        }
                        PeerConnection.IceConnectionState.FAILED ->
                            failCurrentConnection(observerGeneration, "WebRTC failed")
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            live = false
                            failCurrentConnection(observerGeneration, "WebRTC disconnected")
                        }
                        else -> Unit
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                if (isCurrent(observerGeneration)) {
                    sendSignal("webrtc/candidate", candidate.sdp)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(
                receiver: RtpReceiver,
                mediaStreams: Array<out MediaStream>,
            ) {
                attachRemoteTrack(receiver.track(), observerGeneration)
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                attachRemoteTrack(transceiver.receiver.track(), observerGeneration)
            }
        }

    private fun attachRemoteTrack(track: MediaStreamTrack?, trackGeneration: Int) {
        if (!isCurrent(trackGeneration)) return
        when (track) {
            is VideoTrack -> {
                remoteVideoTrack?.removeSink(renderingSink)
                remoteVideoTrack = track
                track.addSink(renderingSink)
            }
            is AudioTrack -> track.setEnabled(!quietMode)
        }
    }

    private fun createDiagnostics(codec: String, decoder: String): DoorbellDiagnostics {
        val memoryInfo = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(memoryInfo)
        return DoorbellDiagnostics(
            width = videoWidth,
            height = videoHeight,
            framesPerSecond = framesPerSecond,
            codec = codec,
            decoder = decoder,
            connectionTimeMs = connectedAt?.let { it - initialConnectStartedAt },
            reconnectCount = reconnectCount,
            memoryPssMb = (memoryInfo.totalPss / 1_024.0).toInt(),
        )
    }

    private fun sendSignal(type: String, value: String) {
        webSocket?.send(
            JSONObject()
                .put("type", type)
                .put("value", value)
                .toString(),
        )
    }

    private fun isCurrent(callbackGeneration: Int): Boolean =
        !stopped && callbackGeneration == generation

    private fun emit(phase: DoorbellSessionPhase, detail: String) {
        mainHandler.post {
            if (phase == DoorbellSessionPhase.STOPPED || !stopped) {
                onStatus(DoorbellSessionStatus(phase, detail))
            }
        }
    }

    private fun buildSignalingUrl(baseUrl: String, streamName: String): String {
        val baseUri = Uri.parse(baseUrl)
        return baseUri.buildUpon()
            .scheme(if (baseUri.scheme == "https") "wss" else "ws")
            .path("/api/ws")
            .clearQuery()
            .appendQueryParameter("src", streamName)
            .build()
            .toString()
    }

    private open inner class SimpleSdpObserver(
        private val observerGeneration: Int,
    ) : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) {
            mainHandler.post {
                failCurrentConnection(observerGeneration, "SDP offer failed")
            }
        }
        override fun onSetFailure(error: String) {
            mainHandler.post {
                failCurrentConnection(observerGeneration, "SDP negotiation failed")
            }
        }
    }

    private companion object {
        const val TAG = "NativeDoorbell"
        const val CONNECTION_TIMEOUT_MS = 10_000L
        const val MAX_RETRY_ATTEMPTS = 6
    }
}
