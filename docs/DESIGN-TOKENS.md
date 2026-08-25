# Design tokens

Every size, gap, radius and text size the Compose pages draw comes from
`ui/theme/PanelMetrics.kt`, alongside the colours in `ui/theme/PanelColors.kt`.
No page contains a `.dp` or `.sp` literal. A restyle is an edit to those two
files, or a different set of values passed to `PanelThemeProvider`.

## What the tokens are, and are not

They are an inventory, not a scale. Each token holds the exact value the
hand-built dashboard used, so introducing them changed nothing on screen —
which is what made the change verifiable.

That fidelity preserved some inconsistencies. They look like accidents of
per-spot tuning rather than decisions, and a redesign is the right place to
resolve them rather than a refactor that was supposed to change nothing:

| Tokens | Values | Why it looks accidental |
| --- | --- | --- |
| `type.forecastSymbol` / `type.hourlySymbol` | 19 sp / 18 sp | The same weather glyph, one point apart, in two cards on the same page |
| `radius.card` / `cardHourly` / `cardLarge` | 18 / 19 / 20 dp | Three card radii within one screen; `cardSmall` at 15 dp is a deliberate contrast, these three are not |
| `type.pageTitle` / `type.cardHeadline` | both 17 sp | Same value, different roles — kept apart so a redesign can move one without the other |
| `space.cardInset` family | 5 / 7 / 9 / 10 / 12 dp | Five card insets, no rhythm between them |

Collapsing these is a one-line edit per row now that the pages reference names.

## Conventions

- **Text sizes** are roles, not sizes: `type.reading` is the big number on a
  tile, and stays that whatever the redesign sets it to.
- **`space`** is gaps and insets, **`size`** is fixed component dimensions
  (an icon, a button, the tile height), **`radius`** is corner rounding.
- Stadium shapes use `CircleShape` rather than a radius token equal to half the
  height, so they stay stadium-shaped when the height changes.
- `PanelText` with no size argument means `type.body`; `PanelCard` with no
  radius argument means `radius.card`.

## Not covered

The modal dialogs — administration, cover control, fan speed, timers,
schedules — are still Android `Dialog`s built from views in `MainActivity` and
`PanelDashboardView`. They take none of these tokens and are dressed by the
platform's default dialog theme. Restyling them means converting them to
Compose.
