# Applying these rules to this project (Android TV / Compose for TV)

This project is an Android TV launcher, not a tvOS app. Use SKILL.md for the design intent, and this file to translate it.

## Units
- A 1080p Android TV runs at xhdpi: the screen is 960x540dp, 1dp = 2px.
- tvOS pt at 1080p = 1px. So divide tvOS pt by 2 to get dp/sp (29pt ≈ 15sp, 48pt ≈ 24sp, 250x150pt card ≈ 125x75dp minimum).
- Previews must use `spec:width=960dp,height=540dp,dpi=320`, not `Devices.TV_1080p`.

## API mapping
| tvOS | Compose for TV |
|---|---|
| Focus state (FOCUS-01) | `androidx.tv.material3.Surface` / `Card` with `ClickableSurfaceDefaults.scale/border/glow` |
| Default focus (FOCUS-06) | `FocusRequester` + `requestFocus()` in `LaunchedEffect` |
| Focus memory (FOCUS-07) | `Modifier.focusRestorer` |
| UIFocusGuide (FOCUS-03) | `Modifier.focusProperties { left/right/up/down = ... }`, `focusGroup()` |
| Menu button (REMOTE-02) | Back key / `OnBackPressedCallback` |
| Click feedback (REMOTE-07) | `ClickableSurfaceDefaults.scale(pressedScale = ...)` |
| Safe area (DISTANCE-06) | Android TV overscan margins: 48dp horizontal, 27dp vertical |
| Top tab bar (TAB-01) | `androidx.tv.material3.TabRow` |

## Project-specific overrides
- REMOTE-02: this app IS the home screen, so Back at the top level is swallowed instead of exiting.
- Not applicable: LSR parallax layers (FOCUS-04), Top Shelf (SHELF-*), Siri (REMOTE-06), AVKit APIs.
