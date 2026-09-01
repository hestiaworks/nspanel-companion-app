package dev.hacompanion.panel

/**
 * What the intercom page asks the activity to do.
 *
 * The page holds no session and no socket — it knows a name and a phase.
 * Passing intent rather than reaching for either keeps the call's machinery
 * in one place, where the session and the socket already live together.
 */
sealed interface IntercomCommand {
    data class Call(val panelId: String) : IntercomCommand
    object Answer : IntercomCommand
    object Decline : IntercomCommand
    data class Mute(val muted: Boolean) : IntercomCommand
    object End : IntercomCommand
}
