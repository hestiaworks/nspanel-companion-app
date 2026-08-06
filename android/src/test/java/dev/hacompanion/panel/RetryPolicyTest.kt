package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun increasesDelayAndCapsAtLastValue() {
        val policy = RetryPolicy(longArrayOf(1, 2, 4))
        assertEquals(1, policy.delayForAttempt(0))
        assertEquals(2, policy.delayForAttempt(1))
        assertEquals(4, policy.delayForAttempt(2))
        assertEquals(4, policy.delayForAttempt(100))
    }
}
