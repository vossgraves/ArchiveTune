# ArchiveTune Fork — v0 Collaboration Memory

This document captures the context, decisions, and work done on the
`vossgraves/ArchiveTune` fork during the v0 chat sessions. It exists so that
future sessions (or contributors) can pick up with full context.

## Repos & branches

- **Fork (this repo):** `vossgraves/ArchiveTune`, default/working branch `main`.
- **Upstream:** `rukamori/ArchiveTune`, active branch `dev`.
- **Nightly/canary (upstream only):** `rukamori/canary` — the fork has no
  equivalent nightly repo.
- CI: the `Build APKs` workflow builds on every push to `main`; `build_debug`
  is the fastest signal that a change compiles. Release variants
  (`gms-mobile-*`, `foss-mobile-*`, `gms-tv-*`) pass once debug compiles.

## Major features added to the fork

- **Tidal lossless source:** WebView login (PKCE + Bearer capture) with
  self-healing token refresh (serialized to survive rotating refresh tokens),
  premium detection via `premiumAccess`/`highestSoundQuality`, HiFi instances
  moved to Integration with on-demand health tests (online/deprecated chips).
  Bundled public instances removed (empty list = disabled, no default fallback).
- **Qobuz lossless source:** user-provided proxy instances
  (get-music/download-music) plus a direct Qobuz API token backend
  (`QobuzToken` model, MD5-signed `getFileUrl`/`search`, token health probe).
  Provider tries tokens first, then proxies via a shared `Backend` abstraction.
  WebView login captures `user_auth_token` + `app_id`; supports bulk paste,
  per-token health test, remove-dead/deprecated actions. FLAC/Hi-Res/Max
  quality, ISRC-free artist+title+duration matching.
- **Multi-source resolver:** preferred-source order is authoritative in the
  resolver (sources after YouTube are ignored). Opt-out toggle for reporting
  non-YouTube plays to YouTube listen history.
- **Integration account cards:** YouTube Music always shown; Last.fm and Discord
  cards are pinnable, float to top, and show live connection status + identity.
- **Backup classification:** Tidal login/session and Qobuz direct-API tokens are
  ACCOUNT keys (travel with Account backups, no longer leak into Settings-only
  exports); instance URL lists stay portable under Settings.

## Player / UI / motion

- **Bottom-nav pill:** custom sliding pill indicator that springs between
  Home/Search/Library, wrapping ONLY the icon (56x32dp), with text labels kept
  visible below. Motion runs inside a fixed `MotionDurationScale(1f)` so the
  slide/icon-pop stay expressive even when the OS animator scale is 0.5x; the
  in-app "disable animations" toggle still fully bypasses animation.
  File: `ui/component/FloatingNavigationToolbar.kt`.
- **App motion:** app-open fade+scale reveal, Material fade-through between
  bottom-nav screens, springy pop on selected nav icon (all respect
  disable-animations).
- **Player glow / color accuracy** (`ui/theme/PlayerColorExtractor.kt`, a
  SHARED extractor used by all player background styles — GRADIENT, COLORING,
  BLUR_GRADIENT, GLOW, GLOW_ANIMATED — plus album/artist/playlist accents):
  - Fixed grey glow on colorful art: greyscale detection now uses PEAK
    saturation among meaningful swatches (ignoring <2% specks) and only forces
    grey when truly monochrome (low peak saturation AND near-grey dominant).
  - Fixed inaccurate glow color: `calculateColorWeight` favors saturation
    (`0.2 + sat*1.35`) and down-weights near-black/near-white swatches so the
    representative vivid color wins over a large dull background.
- **Crossfade:** the aggressive "handoff" rework was REVERTED (it caused
  crashes/instability). Current state uses the original stable crossfade path
  plus a "Crossfading" indicator using a solid theme color (the rainbow/RGB
  shimmer was dropped).
- Removed the broken splash/opening animation (dropped `installSplashScreen()`
  and core-splashscreen); adaptive window background prevents white flash.

## Lyrics

- **LyricsPlus dedup:** the app-local `LyricsPlusLyricsProvider` was a redundant,
  inferior reimplementation of the same service already provided by the
  `:lyrics:youlyplus` submodule (`YouLyPlusLyricsProvider`, richer TTML
  word-sync). The duplicate provider, its enum value
  (`PreferredLyricsProvider.LYRICS_PLUS`), preference key, settings toggle,
  label, and string were all removed. `YouLyPlusLyricsProvider` is the real
  LyricsPlus integration and is untouched. The `"lyricsplus"` search keyword was
  kept because YouLyPlus IS the LyricsPlus service.

## Last.fm (IMPORTANT — nuanced history)

- The entire Last.fm/scrobbling system and the `LASTFM_API_KEY`/`LASTFM_SECRET`
  BuildConfig block are UPSTREAM rukamori code, not fork-authored.
- On a self-built fork WITHOUT rukamori's private Last.fm API-key build secret,
  the login button is greyed out — this is inherent upstream behavior.
- The fork adds a **fork fallback** (currently RE-APPLIED at the user's request):
  for the `LASTFM` provider, `apiKey`/`secret` fall back to the user's own
  registered override when the built-in `BuildConfig` value is blank, and the
  API key/secret credential fields are shown for the official Last.fm provider
  when `builtInLastFmConfigured` is false. This lets fork users register a free
  Last.fm API app and paste their own key/secret to enable login. Official
  builds are unaffected.
  Files: `scrobbling/LastFmSettingsRepository.kt`,
  `viewmodels/LastFmSettingsViewModel.kt`.
- History note: this fallback was added, then reverted to exact upstream, then
  re-added — so if it looks like churn in git log, that's why.

## In-app updates (redirected to the fork)

- `utils/Updater.kt` now points the STABLE update flow at the fork:
  - Releases page: `https://github.com/vossgraves/ArchiveTune/releases`
  - Releases API: `api.github.com/repos/vossgraves/ArchiveTune/releases`
  - Commit changelog: `api.github.com/repos/vossgraves/ArchiveTune/commits`,
    default branch changed from `dev` to `main` (the fork's default branch).
- The canary/nightly source (`rukamori/canary`) was left unchanged because the
  fork has no nightly repo.
- NOTE: update checks will report "no releases found" until GitHub Releases are
  published on the fork with the expected artifact names
  (`app-<dist>-<device>-<arch>-release.apk`).

## Build / CI fixes (for forks without upstream secrets)

- Start.io: `validateStartIoReleaseConfiguration` warns instead of hard-failing
  when `START_IO_APP_ID` is blank (identifier unused at runtime), so GMS release
  APKs build on forks/CI. Official builds still inject it via secret.
- Debug builds sign with a committed `persistent-debug.keystore` for a stable
  signature (no forced uninstall/reinstall between builds).
- **Release APK signing (stable signature across builds/ABIs):** CI previously
  ran `keytool -genkeypair` to create a fresh EPHEMERAL keystore whenever no
  `KEYSTORE` secret was set (the fork case). Because each ABI variant
  (arm64, x86_64, universal, TV, FOSS) is a separate matrix job, even variants
  from the SAME run got different signatures → Android refused in-place updates
  → forced uninstall+reinstall. Fixed: when no `KEYSTORE` secret is present,
  both `.github/workflows/build.yml` and `release.yml` now sign with the
  committed `app/persistent-debug.keystore` (standard `androiddebugkey` /
  `android` creds), so every build and every ABI shares ONE stable signature
  and installs over the previous one. Upstream's real-`KEYSTORE`-secret path is
  unchanged; PR builds (lint-only) were left untouched.
- Various release resource-link/merge fixes (duplicate color resource, Qobuz
  vector `?attr/colorControlNormal` tint moved to call site).

## Settings screen — upstream adopted (fork revamp dropped)

- The fork had a settings revamp: consolidated category cards + a keyword-based
  search + an `icon: Int` model.
- Upstream `dev` reworked settings into a more granular 17-entry list with
  `painterResource` icons and no search — incompatible with the fork's model.
- **Decision (user):** adopt upstream's settings screen and DROP the fork
  revamp. During the `rukamori/dev` merge (commit `9d0ff926`), all four settings
  files (`SettingsScreen.kt`, `SettingsDataBuilders.kt`, `SettingsModels.kt`,
  `SettingsComponents.kt`) were reset to be byte-identical to upstream. The
  playback-source picker/reorder logic still lives in the fork.

## PR status

- PR `rukamori/ArchiveTune#1024` (contributing the fork's features upstream) was
  CLOSED — the maintainer declined it. Work continues on the fork only.
- Note on CI: fork PRs against upstream show `action_required` (a
  maintainer-approval gate GitHub enforces because the workflow uses secrets),
  which looks like a failure but is not. Only the upstream maintainer can
  approve or relax that setting.

## Constraints / working notes for future sessions

- Cannot build Android locally in this environment — validate compilation via
  the fork's `Build APKs` CI (`build_debug` job).
- GPL-3.0 copyright notices (`© Rukamori — github.com/rukamori`) at the top of
  source files MUST be preserved (per GPL-3.0 Section 4 & 5).
- The working checkout at `.forks/at-fork` is periodically reset; re-clone from
  `vossgraves/ArchiveTune` with `--recurse-submodules` when it goes missing.
