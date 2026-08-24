package dev.hacompanion.panel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest

/**
 * The panel half of the shared layout contract.
 *
 * The integration validates the same fixture in test_layout_fixture.py. Testing
 * both sides against identical bytes is what keeps this parser and the Python
 * validator from drifting apart, which co-location used to hide rather than
 * prevent.
 *
 * The publisher_only_invalid cases are deliberately not exercised here: Home
 * Assistant is the gatekeeper and may reject more than the panel does.
 */
class DashboardLayoutFixtureTest {

    private val fixtureBytes: ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE_NAME)) {
            "$FIXTURE_NAME is missing. Run nspanel-companion/schema/sync.sh."
        }.readBytes()

    private val fixture = JSONObject(String(fixtureBytes, Charsets.UTF_8))

    @Test
    fun fixtureMatchesTheSharedCopy() {
        val digest = MessageDigest.getInstance("SHA-256").digest(fixtureBytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            "The layout fixture changed. Run nspanel-companion/schema/sync.sh so the " +
                "integration gets the same bytes, and update this digest in both repositories.",
            LAYOUT_FIXTURE_SHA256,
            digest,
        )
    }

    @Test
    fun fixtureTargetsTheSupportedSchema() {
        assertEquals(DashboardLayout.CURRENT_SCHEMA_VERSION, fixture.getInt("schema_version"))
    }

    @Test
    fun acceptsEveryValidLayout() {
        forEachCase("valid") { name, layout ->
            try {
                DashboardLayout.parse(layout)
            } catch (error: Exception) {
                fail("valid layout rejected: $name (${error.message})")
            }
        }
    }

    @Test
    fun rejectsEveryInvalidLayout() {
        forEachCase("invalid") { name, layout ->
            val parsed = runCatching { DashboardLayout.parse(layout) }
            if (parsed.isSuccess) fail("invalid layout accepted: $name")
        }
    }

    private fun forEachCase(category: String, check: (String, JSONObject) -> Unit) {
        val cases = fixture.getJSONArray(category)
        require(cases.length() > 0) { "fixture category $category is empty" }
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            check(case.getString("name"), case.getJSONObject("layout"))
        }
    }

    private companion object {
        const val FIXTURE_NAME = "layout-fixture.json"

        // Recorded so a one-sided edit fails here instead of silently diverging
        // from the integration. Update it with nspanel-companion/schema/sync.sh.
        const val LAYOUT_FIXTURE_SHA256 =
            "93b98b6b3b64afc19ccdcad3e91a48aae8d6016a3e0f881a5a724178464fc36a"
    }
}
