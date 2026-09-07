# Android TV / Fire TV

## What exists

- **Detection**: `Context.isTvDevice()` in `MainActivity.kt` — true on
  `UI_MODE_TYPE_TELEVISION` *or* `FEATURE_LEANBACK` *or* `FEATURE_TELEVISION`.
  Belt and braces on purpose: some boxes do not set uiMode correctly.
- **Manifest**: `app/src/tv/AndroidManifest.xml` — a separate source set declaring
  `android.software.leanback` required, touchscreen not required, the
  `LEANBACK_LAUNCHER` category and the TV banner.
- **Navigation**: `ui/component/TvNavigationRail.kt` — a reduced item set
  (`Screens.TvMainScreens`), explicit D-pad centre/enter handling via `onKeyEvent`,
  focus-driven scale and colour animation, and `focusProperties { right = … }`
  chaining out of the rail into the content pane.
- **Layout**: `MainActivity` uses the rail when `isTvDevice || windowSizeClass ≥
  medium`, so TV reuses the tablet rail rather than a separate surface, and
  re-focuses the rail when returning to a top-level screen.
- **Build**: the shipped TV variant is `gmsTvUniversal` — universal ABI because TV
  devices are heterogeneous. There is no `foss`+`tv` variant; the fork ships
  GMS-only, deliberately.

## What is not known

Fire TV problems have been reported but **not diagnosed**. No fix here should be
described as one until it is reproduced on a device. Things ruled *in* and *out* so
far, from reading the code only:

- Cast initialisation is not a crash path — `DefaultCastPlaybackRepository.castContext`
  wraps `CastContext.getSharedInstance` in a `runCatching` and degrades to null.
- Play Services client libraries being present in a GMS build on a device with no
  Play Services is not automatically fatal; they generally degrade. So "it is the
  GMS flavour" is a hypothesis, not a finding.
- Focus handling exists in only a handful of components (`Dialog.kt`,
  `Material3SettingsGroup.kt`, `Preference.kt`, `SearchBar.kt`, `Player.kt` and some
  playlist screens). Most of the 60-plus settings screens and every bottom-sheet menu
  have none, so D-pad navigation very likely dead-ends there. This is the most
  plausible "does not work properly" of the candidates, and it is verifiable by
  reading, unlike the others.

When someone does have a Firestick in front of them, the cheap first step is
`ui/screens/settings/LogcatScreen.kt`, which is reachable in-app.

## If you add a TV surface

Give every interactive element a focus target and chain it, the way
`TvNavigationRail` chains `right` into content. A touch-shaped dialog with no
focusable children is unreachable from a remote no matter how it looks.
