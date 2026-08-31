package dev.hacompanion.panel.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.CallPhase
import dev.hacompanion.panel.ui.model.IntercomPeer
import dev.hacompanion.panel.ui.slab.Band
import dev.hacompanion.panel.ui.slab.CellRule
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import kotlinx.coroutines.delay

/**
 * The intercom: a list of panels, a call, and a ring.
 *
 * Three screens in one composable because they are three states of one
 * thing, and a call has to be able to take over the page it started from.
 */
@Composable
fun IntercomPage(
    peers: List<IntercomPeer>,
    phase: CallPhase,
    peerName: String,
    seconds: Int,
    level: Float,
    muted: Boolean,
    onCall: (IntercomPeer) -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMute: () -> Unit,
    onEnd: () -> Unit,
) {
    // A ring that never stops is worse than a missed call. Both ends count
    // the same interval down: the callee declines, the caller gives up, and
    // whichever message arrives second is ignored because the call book has
    // already closed the call.
    val ringSeconds = LocalPanelSize.current.intercomRingSeconds
    LaunchedEffect(phase, peerName) {
        if (phase != CallPhase.IDLE && phase != CallPhase.CONNECTED) {
            delay(ringSeconds * 1000L)
            if (phase == CallPhase.RINGING) onDecline() else onEnd()
        }
    }

    when (phase) {
        CallPhase.IDLE -> Roster(peers, onCall)
        CallPhase.RINGING -> Incoming(peerName, onAnswer, onDecline)
        else -> Call(phase, peerName, seconds, level, muted, onMute, onEnd)
    }
}

@Composable
private fun Roster(peers: List<IntercomPeer>, onCall: (IntercomPeer) -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Band(size.intercomHeader, rule = false) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelText("Intercom", type.subtitle, Modifier.weight(1f), semibold = true, maxLines = 1)
                PanelText(
                    if (peers.size == 1) "1 PANEL" else "${peers.size} PANELS",
                    type.label,
                    semibold = true, muted = true,
                    letterSpacing = type.labelTrackingWide, maxLines = 1,
                )
            }
        }
        if (peers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PanelText(
                    "No other panels are connected", type.bodySmall,
                    muted = true, maxLines = 1,
                )
            }
            return@Column
        }
        // Four rows and their gaps come to 392 of the 394 below the header,
        // so a fifth panel has to scroll rather than be cut off.
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            peers.forEach { peer ->
                Spacer(Modifier.height(size.intercomRowGap))
                Row(
                    Modifier.fillMaxWidth().height(size.intercomRow)
                        .background(colors.card)
                        .clickable(enabled = !peer.busy) { onCall(peer) }
                        .padding(horizontal = LocalPanelSpace.current.edge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PanelText(
                        peer.name, type.body, Modifier.weight(1f),
                        semibold = true, maxLines = 1,
                        color = if (peer.busy) colors.disabled else colors.ink,
                    )
                    PanelText(
                        if (peer.busy) "busy" else "Ready",
                        type.bodySmall,
                        muted = true, maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(size.intercomRowGap))
        }
    }
}

@Composable
private fun Call(
    phase: CallPhase,
    peerName: String,
    seconds: Int,
    level: Float,
    muted: Boolean,
    onMute: () -> Unit,
    onEnd: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        // A live call says so across the top edge, where nothing else does.
        Box(Modifier.fillMaxWidth().height(size.intercomLiveRule).background(colors.accent))
        Band(size.intercomHero, rule = false) {
            Column(
                Modifier.fillMaxSize().padding(
                    start = LocalPanelSpace.current.edge,
                    end = LocalPanelSpace.current.edge,
                    top = 26.dp,
                ),
            ) {
                PanelText(
                    when (phase) {
                        CallPhase.CONNECTED -> "CONNECTED"
                        // Answered, and negotiating. Neither end is calling
                        // any more, and saying so is what tells the person
                        // who just pressed answer that it took.
                        CallPhase.CONNECTING -> "CONNECTING"
                        else -> "CALLING"
                    },
                    type.label,
                    semibold = true, muted = true,
                    letterSpacing = type.labelTrackingWide, maxLines = 1,
                )
                PanelText(
                    peerName, type.callName,
                    Modifier.padding(top = 10.dp),
                    bold = true, maxLines = 1,
                )
                if (phase == CallPhase.CONNECTED) {
                    PanelText(
                        clock(seconds), type.subtitle,
                        Modifier.padding(top = 6.dp),
                        muted = true, maxLines = 1,
                    )
                }
            }
        }
        Meter(level, phase == CallPhase.CONNECTED)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(size.intercomActions)) {
            Secondary("⊘", if (muted) "UNMUTE" else "MUTE", danger = false, onTap = onMute)
            CellRule()
            Secondary("✕", "END", danger = true, onTap = onEnd)
        }
    }
}

/**
 * Twelve bars showing what is arriving from the other end.
 *
 * It is there to prove the link is live, which silence cannot: a quiet call
 * and a dead one look identical without it. The level is the far end's,
 * not ours — our own voice proves nothing about the link.
 */
@Composable
private fun Meter(level: Float, live: Boolean) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    Band(size.intercomMeter) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = LocalPanelSpace.current.edge),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lit = if (!live) 0 else (level.coerceIn(0f, 1f) * BARS).toInt()
            repeat(BARS) { index ->
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight(BAR_HEIGHTS[index % BAR_HEIGHTS.size])
                        .background(if (index < lit) colors.accent else colors.line),
                )
            }
        }
    }
}

@Composable
private fun Incoming(peerName: String, onAnswer: () -> Unit, onDecline: () -> Unit) {
    val colors = LocalPanelColors.current
    val size = LocalPanelSize.current
    val type = LocalPanelType.current
    Column(Modifier.fillMaxSize().background(colors.accent)) {
        Box(
            Modifier.fillMaxWidth().height(size.intercomIncoming)
                .padding(horizontal = LocalPanelSpace.current.edge),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                PanelText(
                    "INCOMING CALL", type.label,
                    semibold = true, color = colors.onAccent,
                    letterSpacing = type.labelTrackingWide, maxLines = 1,
                )
                PanelText(
                    peerName, type.callName,
                    Modifier.padding(top = 8.dp),
                    bold = true, color = colors.onAccent, maxLines = 1,
                )
                PanelText(
                    "Two-way audio · no camera", type.bodySmall,
                    Modifier.padding(top = 6.dp),
                    color = colors.onAccent, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(size.intercomActions)) {
            Secondary("✓", "ANSWER", danger = false, onTap = onAnswer)
            CellRule()
            Secondary("✕", "DECLINE", danger = true, onTap = onDecline)
        }
    }
}

/** The glyph-over-label pair the call screen and the doorbell share. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Secondary(
    glyph: String,
    label: String,
    danger: Boolean,
    onTap: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .background(if (danger) colors.danger else colors.cardSecondary)
            .clickable { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PanelText(
            // Text presentation, or Android draws these from the colour
            // emoji font and the tint is ignored.
            glyph + "︎", type.glyph,
            color = if (danger) colors.onAccent else colors.muted, maxLines = 1,
        )
        PanelText(
            label, type.body,
            Modifier.padding(top = 8.dp),
            bold = true, maxLines = 1,
            letterSpacing = type.labelTrackingWide,
            color = if (danger) colors.onAccent else colors.ink,
        )
    }
}

private fun clock(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)

private const val BARS = 12

/** The resting shape of the meter, so an idle call is not a flat line. */
private val BAR_HEIGHTS = listOf(
    .18f, .40f, .66f, .88f, .52f, .30f, .58f, .76f, .44f, .22f, .34f, .16f,
)
