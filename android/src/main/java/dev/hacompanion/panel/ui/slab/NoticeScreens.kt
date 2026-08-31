package dev.hacompanion.panel.ui.slab

import android.app.Dialog
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelFont

/**
 * One thing a screen offers to do, as a cell in the bar along the bottom.
 *
 * Two is the practical limit at 480 px: a third cell leaves 160 px a piece,
 * which is under the width a two-word label needs at the body size.
 */
data class ScreenAction(
    val label: String,
    val destructive: Boolean = false,
    /**
     * Whether picking it closes the screen.
     *
     * False for an action that may refuse — a rejected address keeps the
     * screen open with what was typed still in it, rather than dropping it
     * and making someone start again.
     */
    val closes: Boolean = true,
    val onPick: () -> Unit = {},
)

/**
 * The bar of actions along the bottom of a screen.
 *
 * Pinned below whatever the screen is showing rather than placed after it,
 * so a body that scrolls never carries its own way out off the bottom.
 */
@Composable
fun ColumnScope.ActionBar(actions: List<ScreenAction>, dismiss: () -> Unit) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Box(Modifier.fillMaxWidth().height(LocalPanelSize.current.stroke).background(colors.line))
    Row(Modifier.fillMaxWidth().height(LocalPanelSize.current.listRow)) {
        actions.forEachIndexed { index, action ->
            if (index > 0) CellRule()
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(colors.cardSecondary)
                    .clickable {
                        if (action.closes) dismiss()
                        action.onPick()
                    },
                contentAlignment = Alignment.Center,
            ) {
                PanelText(
                    action.label, type.body,
                    semibold = true, maxLines = 1,
                    letterSpacing = type.labelTrackingWide,
                    color = if (action.destructive) colors.danger else colors.ink,
                )
            }
        }
    }
}

/**
 * Something the panel has to say, and what can be done about it.
 *
 * The shape every one of these screens shares: a label naming the state, a
 * line saying it in words, supporting prose, and the fixed detail — an
 * address, an identifier — that someone reads off the panel and types
 * somewhere else. That detail is the reason these are screens and not
 * cards: it is small, monospaced and copied by eye.
 */
fun showNoticeScreen(
    context: Context,
    dark: Boolean,
    badge: String,
    title: String,
    message: String = "",
    detail: String = "",
    actions: List<ScreenAction>,
): Dialog = showPanelScreen(context, dark) { dismiss ->
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = LocalPanelSpace.current.edge)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            PanelText(
                badge, type.label,
                muted = true, semibold = true, maxLines = 1,
                letterSpacing = type.labelTrackingWide,
            )
            PanelText(title, type.note, Modifier.padding(top = 12.dp), bold = true)
            if (message.isNotBlank()) {
                PanelText(
                    message, type.bodySmall,
                    Modifier.padding(top = 10.dp),
                    muted = true,
                )
            }
            if (detail.isNotBlank()) {
                PanelText(
                    detail, type.micro,
                    Modifier.padding(top = 18.dp),
                    monospace = true,
                )
            }
        }
        ActionBar(actions, dismiss)
    }
}

/**
 * A report, at length: the diagnostic dump, monospaced and scrolling.
 *
 * Left-aligned and unwrapped in spirit — it is a column of key and value
 * that someone reads down, so it takes a header rather than the centred
 * treatment a short notice gets.
 */
fun showReportScreen(
    context: Context,
    dark: Boolean,
    title: String,
    report: String,
    actions: List<ScreenAction>,
): Dialog = showPanelScreen(context, dark) { dismiss ->
    val colors = LocalPanelColors.current
    EditorHeader(title)
    Column(
        Modifier.fillMaxWidth().weight(1f).background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LocalPanelSpace.current.edge, vertical = 12.dp),
    ) {
        PanelText(report, LocalPanelType.current.micro, monospace = true)
    }
    ActionBar(actions, dismiss)
}

/** One thing to type, and how to treat what is typed. */
data class EntryField(
    val label: String,
    val hint: String = "",
    val initial: String = "",
    val secret: Boolean = false,
)

/**
 * The screens that need a keyboard, which are the ones about addresses.
 *
 * Everything else on this panel is set by tapping, deliberately — but a
 * Home Assistant URL and a token cannot be stepped into existence, and the
 * alternative is a panel that can only ever be set up by something else.
 *
 * The soft keyboard covers the lower two thirds of a 480 px panel, and this
 * window will not resize out from under it — it is laid out over the system
 * bars to get the full height, and Android does not resize such a window.
 * So the keyboard's own action key is made to mean something: Next moves to
 * the following field, Done closes the keyboard and brings the action bar
 * back, with what was typed still in place.
 */
fun showEntryScreen(
    context: Context,
    dark: Boolean,
    title: String,
    message: String,
    fields: List<EntryField>,
    submitLabel: String,
    onSubmit: (List<String>) -> String?,
): Dialog = showPanelScreen(
    context, dark,
    onShow = { dialog ->
        // Panel screens are laid out non-focusable so they can take the
        // whole 480 px without the decor reserving space for system bars.
        // This is the one screen that has to take keyboard input, and a
        // non-focusable window is also one Android will not resize for the
        // keyboard — so it gives that up, and gets Save back.
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    },
) { dismiss ->
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val values = remember { fields.map { mutableStateOf(it.initial) } }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        EditorHeader(title)
        Column(Modifier.fillMaxWidth().weight(1f)) {
            if (message.isNotBlank()) {
                PanelText(
                    message, type.bodySmall,
                    Modifier.padding(
                        start = LocalPanelSpace.current.edge,
                        end = LocalPanelSpace.current.edge,
                        top = 10.dp, bottom = 4.dp,
                    ),
                    muted = true,
                )
            }
            fields.forEachIndexed { index, field ->
                EntryRow(
                    field,
                    values[index].value,
                    last = index == fields.lastIndex,
                ) { values[index].value = it }
            }
            if (error.isNotBlank()) {
                PanelText(
                    error, type.micro,
                    Modifier.padding(
                        start = LocalPanelSpace.current.edge,
                        end = LocalPanelSpace.current.edge,
                        top = 10.dp,
                    ),
                    color = colors.danger,
                )
            }
        }
        ActionBar(
            listOf(
                ScreenAction("CANCEL"),
                ScreenAction(submitLabel, closes = false) {
                    val complaint = onSubmit(values.map { it.value })
                    if (complaint == null) dismiss() else error = complaint
                },
            ),
            dismiss,
        )
    }
}

/** One labelled field, as a band with its own rule. */
@Composable
private fun EntryRow(
    field: EntryField,
    value: String,
    last: Boolean,
    onValue: (String) -> Unit,
) {
    val colors = LocalPanelColors.current
    val type = LocalPanelType.current
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxWidth().height(LocalPanelSize.current.stroke).background(colors.line))
    Column(
        Modifier.fillMaxWidth().background(colors.card)
            .padding(horizontal = LocalPanelSpace.current.edge, vertical = 10.dp),
    ) {
        PanelText(
            field.label, type.label,
            muted = true, semibold = true, maxLines = 1,
            letterSpacing = type.labelTrackingWide,
        )
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (value.isEmpty() && field.hint.isNotBlank()) {
                PanelText(field.hint, type.body, muted = true, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    color = colors.ink,
                    fontSize = type.body,
                    fontFamily = PanelFont.barlow,
                ),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation =
                    if (field.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = if (last) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Down) },
                    onDone = { focus.clearFocus() },
                ),
            )
        }
    }
}
