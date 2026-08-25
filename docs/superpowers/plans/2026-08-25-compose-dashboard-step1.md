# Compose Dashboard — Step 0 and Step 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture the pre-conversion comparison set, then convert the weather page to Jetpack Compose with its data extraction split into pure, unit-tested functions.

**Architecture:** A pure mapping function turns an `EntityState` into a `WeatherModel`; a composable renders that model and nothing else. Colours move from a mutable singleton to a `CompositionLocal`. `PanelDashboardView` keeps building every other page and delegates only its weather page to Compose.

**Tech Stack:** Kotlin 1.8.0, Compose BOM 2023.01.00 (`ui`, `foundation`, `activity-compose`), compose-compiler 1.4.0, JUnit 4, org.json for JVM tests.

**Spec:** `docs/superpowers/specs/2026-08-25-compose-dashboard-design.md`

## Global Constraints

- Branch is `compose`. `main` must stay free of Compose and AndroidX.
- Only `androidx.compose.ui`, `androidx.compose.foundation`, `activity-compose`, `lifecycle-runtime-ktx`, `savedstate-ktx`. **No Material or Material3** — the design is custom and Material would add weight for widgets already written.
- Rendering must not change visibly. Anything a user would notice beyond pixel-level differences means the conversion went wrong.
- No real LAN addresses in this repository. Use `<panel-address>` in docs; the panel's address is in the private hub.
- Release builds for the panel use version code `1000034` and version name `1.0.0-beta.4-compose-step<N>`, so they install over the released build and `beta.4` restores cleanly.
- The released build is restored on the panel at the end of each working session.

## File Structure

| File | Responsibility |
| --- | --- |
| `ui/model/WeatherModel.kt` (create) | `EntityState` → `WeatherModel`; pure, no Android imports |
| `ui/theme/PanelColors.kt` (create) | Colour tokens and `LocalPanelColors` |
| `ui/components/Surfaces.kt` (create) | `PanelCard`, `PanelText` |
| `ui/ComposeHost.kt` (create) | Lifecycle owner for the window root, moved out of the spike file |
| `ui/pages/WeatherPage.kt` (create) | Renders a `WeatherModel` |
| `PanelDashboardView.kt` (modify) | `weatherPage()` delegates to Compose |
| `ComposeWeatherPage.kt` (delete) | Spike file, replaced by the above |
| `src/test/.../ui/model/WeatherModelTest.kt` (create) | JVM tests for the mapping |

---

### Task 1: Capture the pre-conversion comparison set

Screenshots cannot be recovered once conversion starts. The performance baseline is already recorded in the hub; this task adds the visual reference.

**Files:**
- Create: `<scratch>/baseline/page0.png` … `page5.png` (not committed; attach to the hub record)

- [ ] **Step 1: Confirm the released build is installed**

```bash
adb connect <panel-address>:5555
adb -s <panel-address>:5555 shell "dumpsys package dev.hacompanion.panel | grep versionName"
```

Expected: `versionName=1.0.0-beta.4`. If not, restore it:

```bash
cd ../addons && python3 tools/nspanel-updater.py update <panel-address> \
  --github --channel prerelease --yes --set-home --reinstall
```

- [ ] **Step 2: Capture one screenshot per page**

The display must be awake or every capture is black.

```bash
adb -s <panel-address>:5555 shell input keyevent KEYCODE_WAKEUP
for i in 0 1 2 3 4 5; do
  adb -s <panel-address>:5555 shell input keyevent KEYCODE_WAKEUP
  adb -s <panel-address>:5555 shell screencap -p /sdcard/base$i.png
  adb -s <panel-address>:5555 pull /sdcard/base$i.png baseline/page$i.png
  adb -s <panel-address>:5555 shell input swipe 400 240 80 240 200
  adb -s <panel-address>:5555 shell sleep 3
done
```

- [ ] **Step 3: Verify each capture is a real page**

Open each PNG. A file around 2 kB is a black screen, not a page — if any is, wake the display and repeat that capture. Confirm the weather page is among them; it is the one this plan changes.

- [ ] **Step 4: Record where they are**

Append the file locations to `nspanel-companion/docs/PERFORMANCE.md` under the 2026-08-25 baseline row, and commit that hub change.

---

### Task 2: Weather model

**Files:**
- Create: `android/src/main/java/dev/hacompanion/panel/ui/model/WeatherModel.kt`
- Test: `android/src/test/java/dev/hacompanion/panel/ui/model/WeatherModelTest.kt`

**Interfaces:**
- Consumes: `EntityState` from `dev.hacompanion.panel` — `state: String`, `attributes: JSONObject`, `numberAttribute(name: String): Double?`
- Produces: `WeatherModel`, `ForecastEntry`, and `weatherModel(entity: EntityState, forecastDays: Int, showHourly: Boolean): WeatherModel`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherModelTest {
    private fun weather(attributes: String, state: String = "cloudy") =
        EntityState("weather.home", state, JSONObject(attributes))

    @Test
    fun formatsTemperatureWithoutATrailingZero() {
        val model = weatherModel(
            weather("""{"temperature":17.0,"temperature_unit":"°C"}"""), 5, false,
        )
        assertEquals("17°", model.temperature)
    }

    @Test
    fun keepsOneDecimalWhenItCarriesInformation() {
        val model = weatherModel(
            weather("""{"temperature":16.8,"temperature_unit":"°C"}"""), 5, false,
        )
        assertEquals("16.8°", model.temperature)
    }

    @Test
    fun reportsAnEmDashWhenTemperatureIsMissing() {
        val model = weatherModel(weather("""{}"""), 5, false)
        assertEquals("—", model.temperature)
    }

    @Test
    fun describesApparentTemperatureAndHumidity() {
        val model = weatherModel(
            weather("""{"temperature":16.8,"apparent_temperature":15.2,"humidity":63}"""), 5, false,
        )
        assertEquals("Feels like 15.2° · 63%", model.detail)
    }

    @Test
    fun fallsBackToTemperatureWhenApparentIsMissing() {
        val model = weatherModel(weather("""{"temperature":16.8}"""), 5, false)
        assertEquals("Feels like 16.8°", model.detail)
    }

    @Test
    fun limitsDailyForecastToTheRequestedDays() {
        val entries = (1..5).joinToString(",") {
            """{"label":"D$it","condition":"cloudy","temperature":2$it,"templow":1$it}"""
        }
        val model = weatherModel(weather("""{"forecast":[$entries]}"""), 3, false)
        assertEquals(3, model.daily.size)
        assertEquals("D1", model.daily.first().label)
    }

    @Test
    fun readsHighFromTemperatureAndLowFromTemplow() {
        val model = weatherModel(
            weather("""{"forecast":[{"label":"Today","condition":"rainy","temperature":22,"templow":11}]}"""),
            5, false,
        )
        assertEquals("22°", model.daily.first().high)
        assertEquals("11°", model.daily.first().low)
        assertEquals("☂", model.daily.first().symbol)
    }

    @Test
    fun omitsHourlyForecastWhenItIsTurnedOff() {
        val hourly = """{"hourly_forecast":[{"label":"Now","condition":"sunny","temperature":20}]}"""
        assertEquals(0, weatherModel(weather(hourly), 5, false).hourly.size)
        assertEquals(1, weatherModel(weather(hourly), 5, true).hourly.size)
    }

    @Test
    fun distinguishesNightFromDay() {
        assertEquals("☾", weatherModel(weather("""{}""", "clear-night"), 5, false).symbol)
        assertEquals("☀", weatherModel(weather("""{}""", "sunny"), 5, false).symbol)
    }

    @Test
    fun titleCasesTheCondition() {
        assertEquals("Clear night", weatherModel(weather("""{}""", "clear-night"), 5, false).condition)
    }

    @Test
    fun fallsBackToAGeneratedSummary() {
        assertEquals(
            "Sunny conditions continue.",
            weatherModel(weather("""{}""", "sunny"), 5, false).summary,
        )
    }

    @Test
    fun prefersTheSummaryTheIntegrationSupplies() {
        assertEquals(
            "Rain arriving this evening.",
            weatherModel(weather("""{"forecast_summary":"Rain arriving this evening."}"""), 5, false).summary,
        )
    }

    @Test
    fun survivesAnEmptyForecast() {
        val model = weatherModel(weather("""{"forecast":[]}"""), 5, true)
        assertEquals(0, model.daily.size)
        assertEquals(0, model.hourly.size)
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew :android:testDebugUnitTest --tests '*WeatherModelTest*'`
Expected: FAIL — unresolved reference `weatherModel`.

- [ ] **Step 3: Write the mapping**

Values and keys are taken from the current `PanelDashboardView`: `temperature` is the forecast high, `templow` the low, and a missing `condition` falls back to the entity state.

```kotlin
package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One forecast row, already formatted for display. */
data class ForecastEntry(
    val label: String,
    val symbol: String,
    val low: String,
    val high: String,
)

/**
 * Everything the weather page draws, with no entity or JSON left in it.
 *
 * Keeping extraction out of the composable is what makes this half testable on
 * the JVM, where the CI has no device.
 */
data class WeatherModel(
    val symbol: String,
    val temperature: String,
    val condition: String,
    val detail: String,
    val summary: String,
    val daily: List<ForecastEntry>,
    val hourly: List<ForecastEntry>,
)

internal fun formatNumber(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else "%.1f".format(value)

internal fun weatherSymbol(condition: String): String = when (condition.lowercase()) {
    "sunny", "clear-night" -> if (condition == "clear-night") "☾" else "☀"
    "cloudy", "partlycloudy" -> "☁"
    "rainy", "pouring", "lightning-rainy" -> "☂"
    "snowy", "snowy-rainy" -> "❄"
    "fog" -> "≋"
    else -> "◌"
}

internal fun forecastLabel(datetime: String, index: Int, hourly: Boolean): String = runCatching {
    val value = OffsetDateTime.parse(datetime)
    if (hourly) {
        if (index == 0) "Now" else value.format(DateTimeFormatter.ofPattern("HH", Locale.getDefault()))
    } else if (index == 0) "Today" else value.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
}.getOrElse { if (index == 0) if (hourly) "Now" else "Today" else "+$index" }

private fun unitOf(entity: EntityState): String =
    entity.attributes.optString("temperature_unit", "°").let { if (it.contains('°')) "°" else it }

private fun entries(entity: EntityState, attribute: String, limit: Int): List<ForecastEntry> {
    val values = entity.attributes.optJSONArray(attribute) ?: return emptyList()
    val unit = unitOf(entity)
    fun temperature(value: Double?) = value?.let { "${formatNumber(it)}$unit" } ?: "—"
    return buildList {
        for (index in 0 until values.length()) {
            if (size >= limit) break
            val item = values.optJSONObject(index) ?: continue
            add(
                ForecastEntry(
                    label = item.optString("label").ifBlank {
                        forecastLabel(item.optString("datetime"), index, attribute == "hourly_forecast")
                    },
                    symbol = weatherSymbol(item.optString("condition", entity.state)),
                    low = temperature(item.optDouble("templow", Double.NaN).takeUnless(Double::isNaN)),
                    high = temperature(item.optDouble("temperature", Double.NaN).takeUnless(Double::isNaN)),
                ),
            )
        }
    }
}

fun weatherModel(entity: EntityState, forecastDays: Int, showHourly: Boolean): WeatherModel {
    val unit = unitOf(entity)
    val temperature = entity.numberAttribute("temperature")
    val humidity = entity.numberAttribute("humidity")
    return WeatherModel(
        symbol = weatherSymbol(entity.state),
        temperature = temperature?.let { "${formatNumber(it)}$unit" } ?: "—",
        condition = entity.state.replace('-', ' ').replaceFirstChar { it.uppercase() },
        detail = buildString {
            append("Feels like ")
            append(
                entity.numberAttribute("apparent_temperature")?.let(::formatNumber)
                    ?: temperature?.let(::formatNumber) ?: "—",
            )
            append(unit)
            humidity?.let { append(" · ${formatNumber(it)}%") }
        },
        summary = entity.attributes.optString("forecast_summary").ifBlank {
            "${entity.state.replace('-', ' ').replaceFirstChar { it.uppercase() }} conditions continue."
        },
        daily = entries(entity, "forecast", forecastDays),
        hourly = if (showHourly) entries(entity, "hourly_forecast", 6) else emptyList(),
    )
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :android:testDebugUnitTest --tests '*WeatherModelTest*'`
Expected: PASS, 13 tests.

- [ ] **Step 5: Run the whole suite**

Run: `./gradlew :android:testDebugUnitTest`
Expected: PASS, no other test disturbed.

- [ ] **Step 6: Commit**

```bash
git add android/src/main/java/dev/hacompanion/panel/ui/model/WeatherModel.kt \
        android/src/test/java/dev/hacompanion/panel/ui/model/WeatherModelTest.kt
git commit -m "Extract the weather page's data from its drawing

Reading values off an entity and building a view tree were one function, so
neither could be tested without a device. The extraction half is now pure and
covered on the JVM."
```

---

### Task 3: Panel colours as a CompositionLocal

**Files:**
- Create: `android/src/main/java/dev/hacompanion/panel/ui/theme/PanelColors.kt`
- Test: `android/src/test/java/dev/hacompanion/panel/ui/theme/PanelColorsTest.kt`

**Interfaces:**
- Produces: `PanelColors` (data class), `lightPanelColors`, `darkPanelColors`, `LocalPanelColors`, and `PanelThemeProvider(dark: Boolean, content: @Composable () -> Unit)`

- [ ] **Step 1: Write the failing test**

The values must match the existing `PanelTheme` singleton exactly, or the conversion changes appearance.

```kotlin
package dev.hacompanion.panel.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelColorsTest {
    @Test
    fun lightPaletteMatchesTheExistingTokens() {
        assertEquals(Color(0xFFE8E6E2), lightPanelColors.canvas)
        assertEquals(Color(0xFFEFEFEB), lightPanelColors.card)
        assertEquals(Color(0xFF171817), lightPanelColors.ink)
        assertEquals(Color(0xFFF17832), lightPanelColors.accent)
        assertEquals(Color(0xFFDADBD6), lightPanelColors.line)
    }

    @Test
    fun darkPaletteMatchesTheExistingTokens() {
        assertEquals(Color(0xFF121312), darkPanelColors.canvas)
        assertEquals(Color(0xFF262724), darkPanelColors.card)
        assertEquals(Color(0xFFF2F2EE), darkPanelColors.ink)
        assertEquals(Color(0xFFF77730), darkPanelColors.accent)
        assertEquals(Color(0xFF3D3F3B), darkPanelColors.line)
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :android:testDebugUnitTest --tests '*PanelColorsTest*'`
Expected: FAIL — unresolved reference `lightPanelColors`.

- [ ] **Step 3: Write the palette**

```kotlin
package dev.hacompanion.panel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The same tokens the view-based PanelTheme holds, as a value rather than
 * mutable global state, so a theme change is a recomposition instead of an
 * Activity restart.
 */
@Immutable
data class PanelColors(
    val canvas: Color,
    val panel: Color,
    val card: Color,
    val cardSecondary: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentWash: Color,
    val line: Color,
    val disabled: Color,
)

val lightPanelColors = PanelColors(
    canvas = Color(0xFFE8E6E2),
    panel = Color(0xFFF8F8F5),
    card = Color(0xFFEFEFEB),
    cardSecondary = Color(0xFFE7E8E3),
    ink = Color(0xFF171817),
    muted = Color(0xFF747873),
    accent = Color(0xFFF17832),
    accentWash = Color(0xFFF8E0D2),
    line = Color(0xFFDADBD6),
    disabled = Color(0xFFB0B3AE),
)

val darkPanelColors = PanelColors(
    canvas = Color(0xFF121312),
    panel = Color(0xFF1E1F1D),
    card = Color(0xFF262724),
    cardSecondary = Color(0xFF30312E),
    ink = Color(0xFFF2F2EE),
    muted = Color(0xFFABAEA8),
    accent = Color(0xFFF77730),
    accentWash = Color(0xFF59301C),
    line = Color(0xFF3D3F3B),
    disabled = Color(0xFF656862),
)

val LocalPanelColors = staticCompositionLocalOf { lightPanelColors }

/**
 * Named to avoid colliding with the existing `object PanelTheme`, which still
 * serves the view-based pages until they are converted.
 */
@Composable
fun PanelThemeProvider(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPanelColors provides if (dark) darkPanelColors else lightPanelColors,
        content = content,
    )
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew :android:testDebugUnitTest --tests '*PanelColorsTest*'`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add android/src/main/java/dev/hacompanion/panel/ui/theme/PanelColors.kt \
        android/src/test/java/dev/hacompanion/panel/ui/theme/PanelColorsTest.kt
git commit -m "Carry panel colours as a value, not mutable global state

The tokens match the existing singleton exactly; a test asserts that, because
a silent drift here changes how every widget looks."
```

---

### Task 4: Card and text primitives

**Files:**
- Create: `android/src/main/java/dev/hacompanion/panel/ui/components/Surfaces.kt`

**Interfaces:**
- Consumes: `LocalPanelColors` from Task 3
- Produces: `PanelCard(modifier: Modifier, radius: Dp, content: @Composable ColumnScope.() -> Unit)` and `PanelText(text: String, size: TextUnit, modifier: Modifier, bold: Boolean, muted: Boolean, align: TextAlign?, maxLines: Int)`

These have no test of their own: they are pure layout with no logic to assert, and they are verified by the screenshot comparison in Task 6. Their correctness is visual.

- [ ] **Step 1: Write the components**

```kotlin
package dev.hacompanion.panel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.theme.LocalPanelColors

/** The rounded, outlined surface every widget sits on. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPanelColors.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .background(colors.card, shape)
            .border(1.dp, colors.line, shape),
        content = content,
    )
}

/** Panel text. `muted` selects the secondary ink used for supporting detail. */
@Composable
fun PanelText(
    text: String,
    size: TextUnit = 14.sp,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    muted: Boolean = false,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LocalPanelColors.current
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = if (muted) colors.muted else colors.ink,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
        ),
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :android:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/src/main/java/dev/hacompanion/panel/ui/components/Surfaces.kt
git commit -m "Add the card and text primitives the pages share"
```

---

### Task 5: Weather page

**Files:**
- Create: `android/src/main/java/dev/hacompanion/panel/ui/pages/WeatherPage.kt`
- Modify: `android/src/main/java/dev/hacompanion/panel/PanelDashboardView.kt` — the `weatherPage` function
- Delete: `android/src/main/java/dev/hacompanion/panel/ComposeWeatherPage.kt`

**Interfaces:**
- Consumes: `WeatherModel` and `weatherModel(...)` from Task 2; `PanelThemeProvider`, `LocalPanelColors` from Task 3; `PanelCard`, `PanelText` from Task 4
- Produces: `WeatherPage(model: WeatherModel)` and `weatherPageView(context: Context, model: WeatherModel, dark: Boolean): View`

- [ ] **Step 1: Write the composable and its View entry point**

The lifecycle host is kept from the spike: `ComposeView` resolves its recomposer's lifecycle owner from the window root, and the framework `Activity` provides none.

```kotlin
package dev.hacompanion.panel.ui.pages

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hacompanion.panel.ui.components.PanelCard
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.WeatherModel
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

@Composable
fun WeatherPage(model: WeatherModel) {
    Column(Modifier.fillMaxSize()) {
        WeatherSummaryRow(Modifier.fillMaxWidth().weight(1f), model)
        if (model.hourly.isNotEmpty()) {
            HourlyForecastCard(
                Modifier.fillMaxWidth().height(104.dp).padding(top = 7.dp),
                model,
            )
        }
    }
}

@Composable
private fun WeatherSummaryRow(modifier: Modifier, model: WeatherModel) {
    Row(modifier) {
        PanelCard(Modifier.weight(1f).fillMaxSize(), radius = 20.dp) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PanelText(model.symbol, 50.sp, align = TextAlign.Center)
                PanelText(model.temperature, 48.sp, bold = true, align = TextAlign.Center)
                PanelText(model.condition, 18.sp, bold = true, align = TextAlign.Center, maxLines = 1)
                PanelText(model.detail, 13.sp, muted = true, align = TextAlign.Center, maxLines = 2)
            }
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(start = 8.dp)) {
            model.daily.forEach { entry ->
                PanelCard(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp),
                    radius = 15.dp,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PanelText(entry.label, 11.sp, bold = true)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PanelText(entry.symbol, 19.sp)
                        }
                        PanelText(entry.low, 13.sp, muted = true)
                        PanelText("  ${entry.high}", 13.sp, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastCard(modifier: Modifier, model: WeatherModel) {
    PanelCard(modifier, radius = 19.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
            PanelText(model.summary, 14.sp, muted = true, maxLines = 2)
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                model.hourly.forEach { entry ->
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PanelText(entry.label, 9.sp, muted = true, align = TextAlign.Center)
                        PanelText(entry.symbol, 18.sp, align = TextAlign.Center)
                        PanelText(entry.high, 13.sp, bold = true, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * Hosts the page in a View so the existing pager can hold it.
 *
 * Compose creates its recomposer per window and resolves the lifecycle owner
 * from the window root, so the owner is installed there by MainActivity rather
 * than on this view.
 */
fun weatherPageView(context: Context, model: WeatherModel, dark: Boolean): View =
    ComposeView(context).apply {
        setContent {
            PanelThemeProvider(dark) {
                Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
                    WeatherPage(model)
                }
            }
        }
    }
```

- [ ] **Step 2: Move the lifecycle host out of the spike file**

`MainActivity` calls `installComposeHost`, which lives in the spike file that
the next step deletes. Create
`android/src/main/java/dev/hacompanion/panel/ui/ComposeHost.kt` with it, moved
unchanged:

```kotlin
package dev.hacompanion.panel.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * ComposeView requires a ViewTreeLifecycleOwner and a
 * ViewTreeSavedStateRegistryOwner, which the framework Activity this app uses
 * never provides. Supplying a small owner keeps MainActivity's base class as it
 * is, rather than reworking the kiosk Activity to host Compose.
 */
private class ComposeHost : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun resume() {
        // Restoration has to happen before the lifecycle moves past INITIALIZED.
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }
}

/**
 * Compose creates its recomposer per window and resolves the lifecycle owner
 * from the window's root view, so installing it on a ComposeView is too low in
 * the tree. Call once from the Activity against its decor view.
 */
fun installComposeHost(root: View) {
    val host = ComposeHost()
    host.resume()
    root.setViewTreeLifecycleOwner(host)
    root.setViewTreeSavedStateRegistryOwner(host)
}
```

Then add `import dev.hacompanion.panel.ui.installComposeHost` to `MainActivity.kt`.

- [ ] **Step 3: Delete the spike file**

```bash
git rm android/src/main/java/dev/hacompanion/panel/ComposeWeatherPage.kt
```

- [ ] **Step 4: Point the dashboard at it**

In `PanelDashboardView.weatherPage(...)`, replace the whole body — including the spike's early `return composeWeatherPage(...)` and the `@Suppress("UNREACHABLE_CODE")` marker — with:

```kotlin
    private fun weatherPage(title: String, widget: DashboardWidget): View {
        val weather = resolveEntity(widget, "weather")
            ?: return emptyPage(title, "No weather entity found")
        // verticalPage draws the page title, which carries the long press that
        // opens the administrator screen. Only the content below it is Compose.
        return verticalPage(title).apply {
            addView(
                weatherPageView(
                    context,
                    weatherModel(weather, widget.forecastDays, widget.showHourly),
                    PanelTheme.isDark,
                ),
                LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }
```

`PanelTheme.isDark` does not exist yet. Add it to the existing `PanelTheme` object so the Compose page follows the same theme the views use:

```kotlin
    var isDark: Boolean = false; private set
```

and set it inside `apply(...)`, at the top of the function body:

```kotlin
        isDark = dark
```

Add the imports `dev.hacompanion.panel.ui.model.weatherModel` and `dev.hacompanion.panel.ui.pages.weatherPageView` to `PanelDashboardView.kt`.

- [ ] **Step 5: Verify it compiles and the suite passes**

Run: `./gradlew :android:testDebugUnitTest :android:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Draw the weather page with Compose

The page now renders a model it is handed rather than reading attributes off
an entity while building views. Replaces the spike file, which was written to
measure rather than to keep."
```

---

### Task 6: Verify on the panel and record

**Files:**
- Modify: `nspanel-companion/docs/PERFORMANCE.md` (hub repository)

- [ ] **Step 1: Build a release-signed step build**

```bash
set -a; . ~/Projects/ha-companion/release-signing/release-secrets.env; set +a
NSPANEL_VERSION_CODE=1000034 NSPANEL_VERSION_NAME="1.0.0-beta.4-compose-step1" \
  ./gradlew assembleRelease
```

- [ ] **Step 2: Confirm the signer matches the pinned certificate**

```bash
keytool -printcert -jarfile android/build/outputs/apk/release/android-release.apk \
  | awk -F': ' '/SHA256:/{gsub(":","",$2); print tolower($2); exit}'
cat release-signing-certificate.sha256
```

Expected: identical. A mismatch cannot install over the released build.

- [ ] **Step 3: Install and confirm it does not crash**

```bash
adb -s <panel-address>:5555 install -r android/build/outputs/apk/release/android-release.apk
adb -s <panel-address>:5555 logcat -c
adb -s <panel-address>:5555 shell am force-stop dev.hacompanion.panel
adb -s <panel-address>:5555 shell am start -n dev.hacompanion.panel/.MainActivity
adb -s <panel-address>:5555 shell sleep 10
adb -s <panel-address>:5555 logcat -d | grep -c "FATAL EXCEPTION"
```

Expected: `0`. Anything else means the page is not rendering and every number below is meaningless.

- [ ] **Step 4: Screenshot the weather page and compare**

```bash
adb -s <panel-address>:5555 shell input keyevent KEYCODE_WAKEUP
adb -s <panel-address>:5555 shell screencap -p /sdcard/step1.png
adb -s <panel-address>:5555 pull /sdcard/step1.png step1-weather.png
```

Open it beside the Task 1 baseline. Temperature, condition, feels-like, humidity and every forecast row must read the same. Pixel-level differences in text rendering are expected; different values, missing rows or changed layout are not.

- [ ] **Step 5: Measure**

```bash
./tools/measure-panel.sh <panel-address> "step1 / compose weather" 3
```

Compare against the 2026-08-25 baseline: 654 ms start, 41.1% janky, p95 93 ms, p99 136 ms, 23,508 kB.

- [ ] **Step 6: Record the result in the hub**

Add a row to the table in `nspanel-companion/docs/PERFORMANCE.md`, noting that this is a half-converted state carrying both toolkits, so the memory figure is inflated and only the end state is comparable. Commit and push the hub change.

- [ ] **Step 7: Restore the released build**

```bash
cd ../addons && python3 tools/nspanel-updater.py update <panel-address> \
  --github --channel prerelease --yes --set-home --reinstall
```

Expected: `Success: ... now has dev.hacompanion.panel 1.0.0-beta.4 · Home restored.`

- [ ] **Step 8: Push the branch**

```bash
git push origin compose
```
