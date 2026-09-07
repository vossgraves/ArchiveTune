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
- Settings are **not** unreachable by remote, contrary to a first reading. Every
  settings row is a `PreferenceEntry` (`ui/component/Preference.kt`), which is
  clickable and therefore a focus target, and `SwitchPreference` routes the whole row
  through it. `ListPreference`, `EnumListPreference` and friends do the same.
- What was wrong there: `PreferenceEntry` chained `Modifier.focusable()` *and*
  `Modifier.clickable()`. `clickable` is already focusable, so every row had two focus
  targets and a remote stopped on one of them that did nothing when OK was pressed.
  The redundant modifier is gone (2026-09-07).
- Still unaudited: bottom-sheet menus (`ui/menu/`) and the various dialogs. Those are
  built from `ListItem`/custom rows rather than `PreferenceEntry`, so whether they
  take focus has not been checked.

When someone does have a Firestick in front of them, the cheap first step is
`ui/screens/settings/LogcatScreen.kt`, which is reachable in-app.

## If you add a TV surface

Give every interactive element a focus target and chain it, the way
`TvNavigationRail` chains `right` into content. A touch-shaped dialog with no
focusable children is unreachable from a remote no matter how it looks.
