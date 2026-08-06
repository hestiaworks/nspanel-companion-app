package dev.hacompanion.panel

import android.content.Context

object MicUsageTracker {
    private const val STORE = "mic_privacy"
    private const val ACTIVE = "active"
    private const val LAST_USED = "last_used_ms"

    fun setActive(context: Context, active: Boolean) {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val wasActive = store.getBoolean(ACTIVE, false)
        store.edit().putBoolean(ACTIVE, active).apply {
            if (active || wasActive) putLong(LAST_USED, System.currentTimeMillis())
        }.apply()
    }

    fun recentlyUsed(context: Context, lingerSeconds: Int): Boolean {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return store.getBoolean(ACTIVE, false) ||
            System.currentTimeMillis() - store.getLong(LAST_USED, 0L) <= lingerSeconds * 1_000L
    }
}
