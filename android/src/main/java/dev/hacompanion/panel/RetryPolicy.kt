package dev.hacompanion.panel

class RetryPolicy(
    private val delaysMillis: LongArray = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000),
) {
    fun delayForAttempt(attempt: Int): Long =
        delaysMillis[attempt.coerceIn(0, delaysMillis.lastIndex)]
}
