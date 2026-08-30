package dev.hacompanion.panel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Being told you were unpaired is only useful if something acts on it.
 *
 * The wiring is a websocket callback into an Activity, which cannot be
 * exercised on the JVM, so this asserts the connection exists rather than
 * letting it be silently dropped by a later refactor.
 */
class RevokedHandlingTest {

    private fun source(name: String) =
        File("src/main/java/dev/hacompanion/panel/$name").readText()

    @Test
    fun `a revoked message reaches the activity that clears the pairing`() {
        assertTrue(
            "PanelApiClient no longer dispatches revoked",
            source("PanelApiClient.kt").contains("\"revoked\" -> handler.post { onRevoked() }"),
        )
        assertTrue(
            "MainActivity no longer clears the pairing when revoked",
            source("MainActivity.kt").contains("onRevoked = ::handlePairingRevoked"),
        )
    }
}
