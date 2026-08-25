# Compose dashboard — design

Convert the panel dashboard from hand-built Views to Jetpack Compose on the
`compose` branch, then decide from measurements whether to keep it.

## Why this is being tried

`PanelDashboardView` is one 1488-line class holding entity state, deciding when
to refresh, extracting values from entities, and building view trees. It already
implements a cruder form of recomposition: a `states` map, `entityBindings`
keyed by entity id, a dirty set, a 50 ms debounce, and a refresh that calls
`removeAllViews()` and rebuilds an entire card for a single entity change. An
`interactionActive` flag exists to suppress that rebuild while a finger is down,
because the rebuild would destroy the view being touched.

Compose replaces that machinery, updates only what read the changed value, and
makes the theming work that is next on the roadmap tractable.

## Decision criterion

**Responsiveness decides.** This app exists because a WebView-based dashboard
was unusable on this hardware; touches, swipes and controls giving immediate
feedback is the property being protected. A Compose build that passes every
memory check and feels worse has failed.

Baseline, measured on the panel with `tools/measure-panel.sh`:

| Cold start | Janky frames | p95 | p99 | PSS |
| --- | --- | --- | --- | --- |
| 654 ms | 41.1% | 93 ms | 136 ms | 23,508 kB |

**Discard if the end state is meaningfully worse on jank or frame times.** The
41% figure is dominated by page transitions, where the current architecture
rebuilds an entire page; Compose updating only what changed is the reason to
expect improvement rather than regression. If it regresses instead, that is the
answer.

**Memory has a hard ceiling of 51,200 kB (50 MB)** and is otherwise a sanity
check. The panel has 1960 MiB and carries that comfortably. One page converted
measured 26,880 kB, so a full conversion would have to double the app's
footprint to fail. A watch level of 32,000 kB is a point to investigate growth,
not a failure.

Protocol and records live in the private hub's `docs/PERFORMANCE.md`.

## Architecture

```
ui/state/DashboardState.kt   entity map, layout, weather freshness
ui/theme/PanelTheme.kt       colours as a CompositionLocal
ui/model/                    EntityState -> page models (pure, JVM-testable)
ui/components/               Card, PanelText, ControlIcon, PanelSlider,
                             ThermostatDial, StatusBar
ui/pages/                    Weather, Thermostat, Controls, General, Camera
ui/DashboardScreen.kt        pager, status bar, idle return
```

| Today | Becomes |
| --- | --- |
| `states` + `entityBindings` + `EntityBinding` | `SnapshotStateMap` in `DashboardState` |
| `scheduleEntityRefresh`, `flushEntityRefreshes`, `dirtyEntityIds`, `entityRefreshPending` | recomposition |
| `boundEntityView` rebuild | composables reading state directly |
| `interactionActive` | unnecessary; nothing is torn down mid-touch |
| `PanelTheme` mutable singleton | `CompositionLocalProvider` |
| value extraction inside page builders | pure functions in `ui/model/` |

`MainActivity` hosts `ComposeView { DashboardScreen(state) }` and installs a
lifecycle owner on the window root, which Compose requires because the Activity
extends the framework `Activity` rather than `ComponentActivity`. Installing it
on the ComposeView is not sufficient: the recomposer is created per window and
resolves its owner from the window root.

`PanelDashboardView` is deleted at the end. `CameraPageView` survives, wrapped
in `AndroidView`, because it hosts a WebRTC `SurfaceViewRenderer`.

## Data flow

`HomeAssistantClient` already marshals every callback through a main-thread
handler, so all state writes happen on the main thread and need no additional
marshalling.

`MainActivity` keeps its existing entry points — `activateInitialStates`,
`activateEntityState`, `activateDashboardLayout` — writing to `DashboardState`
instead of to the view.

The 50 ms debounce is removed. It exists to coalesce full-subtree rebuilds;
Compose already coalesces to at most one recomposition per frame and only for
readers of the keys that changed.

**To verify, not assume:** `activateDashboardLayout` calls `recreate()` when the
theme changes, restarting the Activity so the mutable `PanelTheme` singleton is
re-read. With the theme as a `CompositionLocal` this should become a plain
recomposition. Confirm on device before removing the restart.

## Conversion order

Primitives are not front-loaded; each arrives with its first real caller.

0. Capture the comparison set: screenshot every page and record PSS from the
   current build. Screenshots cannot be recovered after conversion begins.
1. Theme and weather page — `PanelTheme` local, `Card`, `PanelText`
2. General / sensor page
3. Controls page — `ControlIcon`, `PanelSlider`
4. Thermostat page — `ThermostatDial`
5. Camera page — `AndroidView` wrapper
6. Pager, status bar, idle return; `MainActivity` hosts Compose;
   `PanelDashboardView` deleted — `StatusBar`

## Verification

CI has no emulator, so rendering is verified on the panel. After every step:

1. Build release-signed as `1.0.0-beta.4-compose-step<N>` with version code
   `1000034`, so it installs over the current build and the released build
   restores cleanly afterwards
2. `adb install -r`, which preserves pairing and layout
3. Screenshot each page and compare against the step 0 baseline
4. `tools/measure-panel.sh <panel-address> "step<N>" 3`, appended to the
   performance record — cold start, jank and memory, in that order of
   importance
5. Interaction that cannot be judged from a screenshot — sliders, the
   thermostat dial, swipe feel — is exercised by hand on the panel

Logic keeps automated coverage: the `ui/model/` functions are pure and unit
tested on the JVM alongside the existing tests. That is more of the dashboard
under test than today, where extraction and view building are entangled.

**Readings before step 6 are inflated.** Any half-converted state carries both
toolkits. Only the end state is comparable with the baseline, and the record
says so next to each intermediate number.

## Risks

- **`MainActivity` holds the kiosk behaviour** — boot, Home selection, immersive
  mode, doorbell overlay, watchdog — all validated on real hardware. Hosting
  Compose there is the riskiest part of the plan. Step 6 verifies those
  behaviours explicitly on the device rather than inferring them from a build
  that starts.
- **Silent failures are the danger, not loud ones.** A blank page and a crash
  loop both produce plausible memory numbers. Screenshots at every step, and the
  measurement script fails a run whose log contains a fatal exception.
- **The doorbell path is out of scope but reachable.** `DoorbellActivity` and
  the WebRTC session are untouched, but `MainActivity` triggers the overlay and
  step 6 changes `MainActivity`. Doorbell video and two-way audio are verified
  on the device at step 6; the intercom working is one of the reasons this app
  exists.
- **The panel is in daily use.** The released build is restored at the end of
  each working session unless agreed otherwise; steps 3 and 4 touch the controls
  used daily.

## Out of scope

Widget appearance, the layout schema, pairing, the doorbell path, and the HA
frontend. This is a rendering change; anything visible to the user beyond
pixel-level rendering differences means the conversion went wrong.
