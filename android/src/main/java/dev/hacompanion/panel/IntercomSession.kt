package dev.hacompanion.panel

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.hacompanion.panel.ui.model.CallPhase
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * One intercom call: audio both ways between two panels.
 *
 * Audio only — no renderer, no video track. The signalling is somebody
 * else's problem: this hands out an opaque string and expects the same
 * string to arrive at the other end, which is what lets Home Assistant
 * relay it without holding an opinion about SDP.
 *
 * WebRTC is here for one reason above the others: two hands-free panels in
 * one house feed back without acoustic echo cancellation, and the device
 * module below is where that is switched on. A raw PCM stream would need it
 * hand-rolled, which is not a realistic thing to hand-roll.
 *
 * No ICE servers. Both panels are on the same LAN, so host candidates find
 * each other and a STUN server would be a dependency earning nothing.
 */
class IntercomSession(
    context: Context,
    private val onSignal: (String) -> Unit,
    private val onPhase: (CallPhase) -> Unit,
    private val onLevel: (Float) -> Unit,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val microphone: AudioTrack
    private var peer: PeerConnection? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteReady = false
    private var stopped = false

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions(),
        )
        val audioDevice = JavaAudioDeviceModule.builder(appContext)
            // Voice communication is what turns on the platform's echo
            // canceller and gain control; MIC would give us the raw feed and
            // a howl the moment both speakers are up.
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .createPeerConnectionFactory()
        audioDevice.release()
        audioSource = factory.createAudioSource(MediaConstraints())
        microphone = factory.createAudioTrack("intercom_microphone", audioSource)
    }

    /** Ring the other end: we make the offer. */
    fun call() {
        val connection = open() ?: return
        connection.createOffer(
            describe { local ->
                connection.setLocalDescription(silent(), local)
                emit("offer", local.description)
            },
            audioOnly(),
        )
    }

    /** Answer a ring: their offer, our answer. */
    fun accept(offer: String) {
        val connection = open() ?: return
        connection.setRemoteDescription(
            describeDone {
                remoteReady = true
                drainCandidates()
                connection.createAnswer(
                    describe { local ->
                        connection.setLocalDescription(silent(), local)
                        emit("answer", local.description)
                    },
                    audioOnly(),
                )
            },
            SessionDescription(SessionDescription.Type.OFFER, offer),
        )
    }

    /**
     * A signal from the other end.
     *
     * Candidates that arrive before the remote description is set are held:
     * WebRTC rejects them otherwise, and on a LAN they routinely arrive
     * first because there is no round trip to slow them down.
     */
    fun receive(signal: String) {
        val message = runCatching { JSONObject(signal) }.getOrNull() ?: return
        when (message.optString("kind")) {
            "answer" -> peer?.setRemoteDescription(
                describeDone { remoteReady = true; drainCandidates() },
                SessionDescription(SessionDescription.Type.ANSWER, message.optString("sdp")),
            )
            "candidate" -> {
                val candidate = IceCandidate(
                    message.optString("mid"),
                    message.optInt("index"),
                    message.optString("candidate"),
                )
                if (remoteReady) peer?.addIceCandidate(candidate)
                else pendingCandidates += candidate
            }
        }
    }

    fun setMuted(muted: Boolean) {
        microphone.setEnabled(!muted)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching { peer?.dispose() }
        peer = null
        runCatching { audioSource.dispose() }
        runCatching { factory.dispose() }
        onPhase(CallPhase.IDLE)
    }

    private fun open(): PeerConnection? {
        if (stopped) return null
        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val connection = factory.createPeerConnection(configuration, observer()) ?: return null
        connection.addTrack(microphone, listOf("intercom"))
        peer = connection
        return connection
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            emit(
                "candidate",
                candidate.sdp,
                mapOf("mid" to candidate.sdpMid, "index" to candidate.sdpMLineIndex),
            )
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            handler.post {
                phaseFor(state)?.let(onPhase)
                if (state == PeerConnection.PeerConnectionState.CONNECTED) pollLevel()
            }
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            // Remote audio plays through the device module without being
            // attached to anything: there is nothing to render.
            (receiver?.track() as? AudioTrack)?.setEnabled(true)
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(channel: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
            (transceiver?.receiver?.track() as? MediaStreamTrack)?.setEnabled(true)
        }
    }

    /**
     * The level meter's number, read from the inbound stream.
     *
     * It is the far end's audio, not ours: the meter exists to prove the
     * link is live, and our own voice proves nothing about that.
     */
    private fun pollLevel() {
        if (stopped) return
        peer?.getStats { report ->
            val level = report.statsMap.values
                .firstOrNull { it.type == "inbound-rtp" && it.members["kind"] == "audio" }
                ?.members?.get("audioLevel") as? Double
            handler.post { onLevel(level?.toFloat() ?: 0f) }
        }
        handler.postDelayed({ pollLevel() }, LEVEL_INTERVAL_MS)
    }

    private fun drainCandidates() {
        pendingCandidates.forEach { peer?.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    private fun emit(kind: String, value: String, extra: Map<String, Any?> = emptyMap()) {
        val payload = JSONObject().put("kind", kind)
        if (kind == "candidate") payload.put("candidate", value) else payload.put("sdp", value)
        extra.forEach { (key, item) -> payload.put(key, item) }
        handler.post { onSignal(payload.toString()) }
    }

    private fun audioOnly() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    private fun describe(onCreated: (SessionDescription) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onCreated(description)
        override fun onCreateFailure(error: String?) { Log.w(TAG, "Offer failed: $error") }
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) { Log.w(TAG, "Set failed: $error") }
    }

    private fun describeDone(onSet: () -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetSuccess() = onSet()
        override fun onSetFailure(error: String?) { Log.w(TAG, "Remote rejected: $error") }
    }

    private fun silent() = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) { Log.w(TAG, "Local rejected: $error") }
    }

    companion object {
        private const val TAG = "NSPanelIntercom"
        private const val LEVEL_INTERVAL_MS = 200L
    }
}

/**
 * What a peer connection state means for the call on screen, or null where
 * it means nothing worth showing.
 *
 * DISCONNECTED is not the end of a call. WebRTC reports it whenever consent
 * checks miss for a moment — routine on Wi-Fi — and then recovers on its
 * own, or gives up and reports FAILED. Hanging up on it ended healthy calls
 * after about half a minute, with both panels simply going dark. It shows
 * as CONNECTING instead, which is both true and bounded: the same deadline
 * that gives up on an unanswered ring gives up on a call that never comes
 * back.
 */
fun phaseFor(state: PeerConnection.PeerConnectionState): CallPhase? = when (state) {
    PeerConnection.PeerConnectionState.CONNECTED -> CallPhase.CONNECTED
    PeerConnection.PeerConnectionState.DISCONNECTED -> CallPhase.CONNECTING
    PeerConnection.PeerConnectionState.FAILED,
    PeerConnection.PeerConnectionState.CLOSED -> CallPhase.IDLE
    else -> null
}
