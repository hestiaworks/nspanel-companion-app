package dev.hacompanion.panel

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

/**
 * SPIKE ONLY - a Compose rendering of the weather page, built to measure what
 * Compose costs in APK size and memory on the panel. Not wired into the app.
 */
data class ComposeForecast(val label: String, val symbol: String, val low: String, val high: String)

data class ComposeWeather(
    val symbol: String,
    val temperature: String,
    val condition: String,
    val detail: String,
    val daily: List<ComposeForecast>,
)

private val CARD = Color(0xFF16191C)
private val EDGE = Color(0xFF23282C)
private val PRIMARY = Color(0xFFF2F5F6)
private val SECONDARY = Color(0xFF8FA0A6)

@Composable
fun WeatherPage(weather: ComposeWeather) {
    Row(Modifier.fillMaxSize().padding(8.dp)) {
        Column(
            Modifier.weight(1f).fillMaxSize()
                .background(CARD, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(weather.symbol, style = TextStyle(color = PRIMARY, fontSize = 50.sp))
            BasicText(
                weather.temperature,
                style = TextStyle(color = PRIMARY, fontSize = 48.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                weather.condition,
                style = TextStyle(color = PRIMARY, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(weather.detail, style = TextStyle(color = SECONDARY, fontSize = 13.sp))
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(start = 8.dp)) {
            weather.daily.forEach { period ->
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp)
                        .background(CARD, RoundedCornerShape(15.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        period.label,
                        style = TextStyle(color = PRIMARY, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BasicText(period.symbol, style = TextStyle(color = PRIMARY, fontSize = 19.sp))
                    }
                    BasicText(period.low, style = TextStyle(color = SECONDARY, fontSize = 13.sp))
                    BasicText(
                        "  ${period.high}",
                        style = TextStyle(color = PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

/**
 * ComposeView requires a ViewTreeLifecycleOwner and a ViewTreeSavedStateRegistryOwner,
 * which a plain android.app.Activity never provides. Supplying a small owner here keeps
 * MainActivity as it is, rather than reworking the kiosk Activity for a measurement.
 */
class ComposeHost : LifecycleOwner, SavedStateRegistryOwner {
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
 * Compose creates its recomposer per window and resolves the lifecycle owner from
 * the window's root view, so installing it on the ComposeView itself is too low in
 * the tree. Called once from the Activity against its decor view.
 */
fun installComposeHost(root: View) {
    val host = ComposeHost()
    host.resume()
    root.setViewTreeLifecycleOwner(host)
    root.setViewTreeSavedStateRegistryOwner(host)
}

/** Returns the Compose weather page as a plain View, so it can slot into the pager. */
fun composeWeatherPage(context: Context, weather: ComposeWeather): View =
    ComposeView(context).apply {
        setBackgroundColor(EDGE.value.toInt())
        setContent { WeatherPage(weather) }
    }
