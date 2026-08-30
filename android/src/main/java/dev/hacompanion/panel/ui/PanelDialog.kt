package dev.hacompanion.panel.ui

import android.app.Dialog
import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelThemeProvider
import kotlin.math.roundToInt

/**
 * A modal drawn with the same design tokens as the pages.
 *
 * The dialogs were the last surface the panel drew from hardcoded colours and
 * sizes, so a restyle reached every page and stopped at the first dialog.
 *
 * Each dialog is its own window, and Compose builds a recomposer per window
 * from the lifecycle owner on its root, so every dialog needs its own host —
 * the one installed on the Activity's decor view does not reach here.
 */
fun showPanelDialog(
    context: Context,
    dark: Boolean,
    widthFraction: Float = .9f,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
): Dialog {
    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val view = ComposeView(context)
    dialog.setContentView(view)
    dialog.setCanceledOnTouchOutside(true)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setDimAmount(.65f)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        installComposeHost(decorView)
    }
    view.setContent {
        PanelThemeProvider(dark) {
            val colors = LocalPanelColors.current
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.card, RoundedCornerShape(LocalPanelRadius.current.card))
                    .padding(
                        horizontal = LocalPanelSpace.current.edge,
                        vertical = LocalPanelSpace.current.edge,
                    ),
            ) {
                content { dialog.dismiss() }
            }
        }
    }
    dialog.show()
    dialog.window?.setLayout(
        (context.resources.displayMetrics.widthPixels * widthFraction).roundToInt(),
        WindowManager.LayoutParams.WRAP_CONTENT,
    )
    return dialog
}

/** The title and supporting line every panel dialog opens with. */
@Composable
fun PanelDialogHeader(title: String, subtitle: String) {
    val space = LocalPanelSpace.current
    val type = LocalPanelType.current
    PanelText(title, type.title, bold = true)
    PanelText(
        subtitle,
        type.bodySmall,
        Modifier.padding(top = space.edge, bottom = space.unconfiguredBodyGap),
        muted = true,
    )
}

/** A grid of tappable choices, two across, as the timer presets are drawn. */
@Composable
fun PanelDialogChoices(
    labels: List<String>,
    selected: (String) -> Boolean,
    onChoose: (String) -> Unit,
) {
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    Column(Modifier.fillMaxWidth()) {
        labels.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    Box(Modifier.weight(1f).padding(space.edge)) {
                        PanelDialogButton(
                            label = label,
                            height = size.presetCell,
                            active = selected(label),
                        ) { onChoose(label) }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** A full-width action, optionally destructive. */
@Composable
fun PanelDialogAction(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = LocalPanelColors.current
    val space = LocalPanelSpace.current
    val size = LocalPanelSize.current
    Box(Modifier.fillMaxWidth().padding(top = space.edge)) {
        PanelDialogButton(
            label = label,
            height = size.confirmButton,
            active = false,
            ink = if (destructive) colors.danger else colors.ink,
            bold = !destructive,
        ) { onClick() }
    }
}

@Composable
fun PanelDialogButton(
    label: String,
    height: Dp,
    active: Boolean,
    ink: Color? = null,
    bold: Boolean = true,
    radius: Dp = Dp.Unspecified,
    onClick: () -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val size = LocalPanelSize.current
    val shape = RoundedCornerShape(radius.takeOrElse { LocalPanelRadius.current.card })
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (active) colors.accent else colors.panel, shape)
            .border(size.stroke, if (active) colors.accent else colors.line, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        PanelText(
            label,
            type.body,
            bold = bold,
            color = ink ?: if (active) Color.White else colors.ink,
            align = TextAlign.Center,
        )
    }
}
