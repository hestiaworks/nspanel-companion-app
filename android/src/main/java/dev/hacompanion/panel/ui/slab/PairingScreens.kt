package dev.hacompanion.panel.ui.slab

import android.app.Dialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.theme.PanelThemeProvider
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType

/**
 * The screen a panel shows before it belongs to anything.
 *
 * It was the last thing in the app still built from plain views with sizes
 * typed into it, which showed: a platform button and a grey wash, on a panel
 * whose every other screen is bands and rules.
 */
fun pairingOnboardingView(
    context: Context,
    dark: Boolean,
    panelName: String,
    deviceId: String,
    onManual: () -> Unit,
): ComposeView = ComposeView(context).apply {
    setContent {
        PanelThemeProvider(dark) {
            val colors = LocalPanelColors.current
            val type = LocalPanelType.current
            val size = LocalPanelSize.current
            Column(Modifier.fillMaxSize().background(colors.canvas)) {
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .padding(horizontal = LocalPanelSpace.current.edge),
                    verticalArrangement = Arrangement.Center,
                ) {
                    PanelText(
                        "NOT PAIRED", type.label,
                        muted = true, semibold = true,
                        letterSpacing = type.labelTrackingWide, maxLines = 1,
                    )
                    PanelText(
                        "Set up this panel", type.note,
                        Modifier.padding(top = 12.dp),
                        bold = true, maxLines = 1,
                    )
                    PanelText(
                        "Open NSPanel Companion in Home Assistant, choose Find panels, " +
                            "select this panel, and enter the code it shows.",
                        type.bodySmall,
                        Modifier.padding(top = 10.dp),
                        muted = true,
                    )
                    PanelText(
                        "$panelName · ${deviceId.takeLast(12)}",
                        type.micro,
                        Modifier.padding(top = 18.dp),
                        muted = true, maxLines = 1,
                    )
                }
                Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
                Box(
                    Modifier.fillMaxWidth().height(size.listRow)
                        .background(colors.cardSecondary)
                        .clickable { onManual() },
                    contentAlignment = Alignment.Center,
                ) {
                    PanelText(
                        "ENTER ADDRESS MANUALLY", type.body,
                        semibold = true, maxLines = 1,
                        letterSpacing = type.labelTrackingWide,
                    )
                }
            }
        }
    }
}

/**
 * What the pairing screen is currently saying.
 *
 * The flow that drives it reads as a sequence of moments — asking, waiting,
 * paired, failed — so it sets fields rather than reaching into views, and
 * the screen redraws itself.
 */
class PairingScreenState {
    var badge by mutableStateOf("CONNECTING")
    var settled by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var title by mutableStateOf("Requesting a pairing code…")
    var message by mutableStateOf("")
    var code by mutableStateOf<String?>(null)
    var detail by mutableStateOf("")
    var closeLabel by mutableStateOf("CANCEL")
}

/**
 * Pairing, as a screen rather than a card.
 *
 * Full-bleed like the administrator menu: the code is the one thing on the
 * panel someone is reading from across the room while typing it into a
 * laptop, and it earns the width.
 */
fun showPairingScreen(
    context: Context,
    dark: Boolean,
    state: PairingScreenState,
    onClose: () -> Unit,
): Dialog = showPanelScreen(context, dark) { dismiss ->
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = LocalPanelSpace.current.edge),
            verticalArrangement = Arrangement.Center,
        ) {
            PanelText(
                state.badge, type.label,
                semibold = true, maxLines = 1,
                letterSpacing = type.labelTrackingWide,
                color = when {
                    state.failed -> colors.danger
                    state.settled -> colors.accent
                    else -> colors.muted
                },
            )
            PanelText(
                state.title, type.note,
                Modifier.padding(top = 10.dp),
                bold = true,
            )
            if (state.message.isNotBlank()) {
                PanelText(
                    state.message, type.bodySmall,
                    Modifier.padding(top = 8.dp),
                    muted = true,
                )
            }
            state.code?.let { code ->
                // Spaced into two threes, because six digits read back to a
                // browser one group at a time.
                PanelText(
                    code.chunked(3).joinToString("  "),
                    type.editorTime,
                    Modifier.padding(top = 18.dp),
                    bold = true, maxLines = 1,
                )
            }
            if (state.detail.isNotBlank()) {
                PanelText(
                    state.detail, type.micro,
                    Modifier.padding(top = 16.dp),
                    muted = true, maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(size.stroke).background(colors.line))
        Box(
            Modifier.fillMaxWidth().height(size.listRow)
                .background(colors.cardSecondary)
                .clickable {
                    dismiss()
                    onClose()
                },
            contentAlignment = Alignment.Center,
        ) {
            PanelText(
                state.closeLabel, type.body,
                semibold = true, maxLines = 1,
                letterSpacing = type.labelTrackingWide,
            )
        }
    }
}
