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
- **Per-song "Play from" override:** the resolver honors a per-song source
  override (`SongSourceOverrideKey`, `songId=SOURCE;…`, a Settings-backup key,
  encoded/decoded by `SongSourceOverride` in `AudioSourceConfig.kt`). An override
  forces just that one source (still subject to the 95% title-match gate);
  `YOUTUBE` means "always play this song from YouTube" (skip lossless entirely);
  no override = follow the global order. `MusicService` records which sources
  passed the gate per media id (`resolvedSourcesByMediaId`) and exposes
  `availableSourcesForSong()` / `setSongSourceOverride()`; the latter clears the
  cached stream and re-`prepare()`s the current item so the change is immediate.
- **Download lossless (song menu):** downloads the resolved Tidal/Qobuz stream to
  a user-chosen SAF folder instead of the Media3 cache, so the file survives the
  app. `download/LosslessDownloader.kt` owns the transfer: it runs on its own
  process-lived scope (NOT the composition scope, or closing the menu cancelled
  the download), sniffs the real container from magic bytes rather than trusting
  the URL extension, retries with backoff, and dedupes concurrent requests for one
  track through an atomic `markActive` compare-and-set so two writers can never
  race on a single file. Tagging (title/artist/album + cover art) is applied when
  `LosslessDownloadTagKey` is on. Destination + tag toggle live in Qobuz settings
  (`LosslessDownloadFolderKey`); the folder's persistable Uri grant is re-taken on
  each pick so the choice survives reboots.
- **Qobuz stream metadata:** the quality badge and download tags report the real
  bit depth / sampling rate / bitrate parsed from `getFileUrl`. Sampling rate is
  ROUNDED, not truncated — `44.1 * 1000` is 44099.999… in binary, so `toInt()`
  used to write 44099 Hz into the badge and the FLAC tags. Qobuz never returns a
  file size, so `probeContentLength()` asks the CDN (HEAD, falling back to a
  one-byte ranged GET reading the total out of `Content-Range`, since some edges
  reject HEAD on signed URLs). This matters beyond the details sheet: the offline
  check is `downloadCache.isCached(mediaId, 0, contentLength)`, and a 0 length
  always reported "not cached", so Qobuz tracks silently re-downloaded every play.
  The probe gets its own 3s timeout because it sits on the path to the first audio
  frame — a missing size is cheap, making the user wait is not.
- **Integration account cards:** YouTube Music always shown; Last.fm and Discord
  cards are pinnable, float to top, and show live connection status + identity.
- **Backup classification:** Tidal login/session and Qobuz direct-API tokens are
  ACCOUNT keys (travel with Account backups, no longer leak into Settings-only
  exports); instance URL lists stay portable under Settings.

## Player / UI / motion

- **Android Auto Spotify playlists:** the media-library browse tree now restores
  the Spotify playlist cache even when Android Auto launches the service before
  the phone UI. When the existing "Show Spotify playlists" preference is on,
  the Playlists screen contains a Spotify folder with account playlists,
  artwork, track counts, browsable songs, and playable whole-playlist queues.
  Spotify tracks are resolved to ArchiveTune/YouTube media items in bounded
  batches and cached for subsequent browse/play requests.

- **Infinite-queue single-song race fix:** playing one track (e.g. from search)
  used to show infinity-queue "on" while Next just repeated the song, because
  the transition-driven radio bootstrap in `onMediaItemTransition` started mid
  `playQueue` load and got invalidated by the settling transitions (bumping
  `infiniteQueueGeneration`), leaving `infiniteQueueLoading` stuck true with no
  songs queued. Fix (`MusicService.kt`): a `@Volatile initialQueueLoadInProgress`
  flag is set in `playQueue`, guards the transition bootstrap, and is cleared in
  a `finally`; after the queue settles `playQueue` deterministically calls
  `onInfiniteQueueEnabled()` for a single-item, no-next-page, REPEAT_OFF queue —
  mirroring the working manual toggle. Also reset in `cancelInfiniteQueueBootstrap`.
- **Player "Play from" chooser (per song):** the player menu's Source item no
  longer opens the global preferred-order reorder editor. It now opens a
  `SongSourceDialog` (`ui/menu/PlayerMenu.kt`) — a radio list of "Automatic
  (preferred order)" plus the sources known to have THIS track (from the last
  resolution result via `service.availableSourcesForSong`) plus YouTube. Picking
  one writes the per-song override and calls `service.setSongSourceOverride`,
  which re-resolves the current item immediately. The global reorder dialog
  (`SourceOrderDialog`) stays private to `PlaybackSourceSections.kt` for Settings.
- **Streaming-source token status parity:** the Qobuz direct-API token status
  now uses the same ping-based labels as instances — online / deprecated
  (with an info icon explaining preview-only, no premium) / not reachable —
  instead of a bare connected/expired flag. Instance + token health/ping
  results are cached at process level so the status a user saw survives leaving
  and returning to the Integration screen until they explicitly re-check.

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

- **Downloadable Japanese romanization pack / APK size:** Kuromoji IPADIC's eight
  dictionary binaries were the dominant APK payload (13,324,082 compressed
  bytes in release 13.7.5020). Release packaging now excludes those `.bin`
  files while retaining the tokenizer engine. A dedicated top-level Language
  Packs settings screen exposes a Japanese romanization pack that downloads the immutable Kuromoji 0.9.0 jar
  from Maven Central, verifies its pinned SHA-256, stores it under app-private
  `language_packs`, loads dictionary streams from the archive, and supports
  progress/removal/retry. Japanese romanization defaults off and is enabled
  only after installation. English is shown as the base language; existing UI
  translation resources remain because their total footprint is small.
  Korean/Chinese/Hindi/other romanization already uses code or Android ICU and
  has no comparable downloadable data payload.

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

## Tidal / Qobuz integration layout

- The integration pages use progressive disclosure to avoid rendering every
  account, proxy URL, import tool, and destructive cleanup action at once.
- Account sign-in and the primary token/instance health checks stay visible.
  Compact “Manage accounts” / “Manage instances” rows show configured, online,
  deprecated, and failed counts; expanding them reveals individual entries and
  maintenance controls. Existing health colors, ping labels, dialogs, and all
  management capabilities are unchanged.

## ArchivePool security audit (2026-07-17)

- ArchivePool implements AES-256-GCM field encryption, hashed read keys, admin
  bearer checks, HTTPS upstream calls, and no-store response caching, but the
  controls are optional/fail-open when environment variables are missing.
- A live unauthenticated check of `archivepool.up.railway.app` returned HTTP 200
  for `/api/sources` with `"encrypted": false`; both discovery feeds also
  returned 200 without a read key. No returned credential values were printed
  or inspected. The Railway deployment must set independent 32-byte
  `POOL_ENCRYPTION_KEY` and `POOL_CLIENT_KEY` values, provision an app read key,
  set `READ_KEYS_ENFORCED=true`, and build ArchiveTune with matching
  `POOL_CLIENT_KEY` / `SOURCE_PROVIDER_KEY` values.
- ArchiveTune stores user and downloaded pool tokens in ordinary Preferences
  DataStore, and its current Android backup rules do not exclude that settings
  file. A key embedded in `BuildConfig` is extractable from the APK, so the pool
  client-key layer protects accidental JSON disclosure but is not true
  per-user end-to-end secrecy. A future hardening pass should move credentials
  to Android Keystore-backed storage and exclude them from cloud backup.
- The database URL and GitHub token pasted into chat must be treated as
  compromised and rotated; they are not recorded in this memory file.

## Upstream sync (2026-07-17)

- Merged the current `rukamori/ArchiveTune` `dev` into fork `dev`, including AI
  content filtering, playlist-cover synchronization, pull-to-refresh/library
  improvements, updater improvements, translations, and metadata updates.
- Merged the matching `rukamori/core` changes into `vossgraves/core` while
  retaining the fork's neutralized official-build network gatekeeper. Fork
  update URLs and all fork-specific playback, Source Pool, Android Auto, and
  Language Packs features were preserved.
- Canary update detection uses a 15-minute release cache and 30-minute
  background worker instead of Stable's six-hour cadence. Manual checks always
  force a refresh. Canary selection accepts both `NyyyyMMdd` and the fork's
  `NyyyyMMddHHmm` tags and explicitly excludes stable releases.

## PR status

- PR `rukamori/ArchiveTune#1024` (contributing the fork's features upstream) was
  CLOSED — the maintainer declined it. Work continues on the fork only.
- Note on CI: fork PRs against upstream show `action_required` (a
  maintainer-approval gate GitHub enforces because the workflow uses secrets),
  which looks like a failure but is not. Only the upstream maintainer can
  approve or relax that setting.

## Qobuz metadata accuracy + lossless download (branch `feat/anx-merge`)

CI-validated at `8c07e06ad`.

- **Real sample rate / bit depth.** These were previously assumed from the
  requested quality tier; they are now parsed from the `getFileUrl` response.
  Note `44.1 * 1000` truncates to **44099 Hz** through `toInt()`, because 44.1
  and 88.2 have no exact binary representation — `roundToInt()` is required. The
  wrong value reached both the quality badge and the FLAC tags written on
  download.
- **`contentLength` is never returned by Qobuz.** The visible symptom was a blank
  file-size row, but the important one was silent: the offline check in
  `MusicService` is `downloadCache.isCached(mediaId, 0, contentLength)`, and
  given 0 it always answers "not cached" — so Qobuz tracks re-downloaded on
  every play. `QobuzAudioProvider.probeContentLength()` now asks the CDN: `HEAD`
  first, then a one-byte ranged `GET` reading the total after the slash of
  `Content-Range: bytes 0-0/<total>`, since some edges reject `HEAD` on signed
  URLs. It sends `Accept-Encoding: identity` (a gzipping edge would otherwise
  report the compressed size) and deliberately ignores `Content-Length` on the
  ranged reply, where it is 1 rather than the file size.
- **The probe is on the path to first audio.** It runs inside `resolve`, which
  executes in `runBlocking(Dispatchers.IO)` on ExoPlayer's resolver thread.
  `healthClient`'s 8s call timeout applies to each of the two attempts, so a
  stalled edge could delay playback by 16s; the probe therefore gets its own 3s
  budget via `healthClient.newBuilder()`, which still shares the connection pool.
  A size we fail to learn only costs a blank row and a re-download — making the
  user wait to hear anything is worse.
- **Lossless download** is `LosslessDownloader` + `AudioFileTagger` plus
  container sniffing. It runs on a service-lived scope rather than the
  composition scope: tying it to the bottom sheet meant dismissing the menu
  aborted a partially written file. `markActive()` guards against a double-tap
  starting two writers on one file, using an explicit `compareAndSet` loop —
  `MutableStateFlow.update {}` re-invokes its lambda after losing a CAS race, so
  a captured "did I add it" flag can stay set from an attempt that never took
  effect.
- **Settings.** `LosslessDownloadFolderKey` and `LosslessDownloadTagKey` were
  read by `SongMenu` but never writable, leaving the tag switch permanently on
  and a folder unchangeable once chosen. `QobuzSettings.kt` now has a "Lossless
  downloads" group that resolves the stored tree Uri to a display name via
  `DocumentFile.fromTreeUri` and re-takes the persistable permission grant each
  time a folder is picked.

## Cache export + a real test gate (branch `feat/anx-merge`)

- **`download/CacheExporter.kt`.** Exporting downloaded songs used to run inline
  in `CachePlaylistScreen` on the composition scope, so leaving the screen
  cancelled the copy midway and left partial files behind, and the summary toast
  could fire into a dead composition. It also only existed on that one screen.
  The logic now lives in a singleton owning a `SupervisorJob + Dispatchers.IO`
  scope that outlives any composition, with progress exposed as a `StateFlow` so
  a screen reopened mid-run rebinds to the export already in flight rather than
  starting a second one. A second concurrent export is rejected: two runs into
  the same folder race on identical filenames.
- Storage settings gained an "Export downloaded songs" entry, so the feature is
  reachable without going through the cache playlist. It observes `CacheExporter`
  directly instead of mirroring progress into the ViewModel.
- **SAF gotcha.** Create files with `DocumentFile.fromTreeUri(...).createFile()`.
  `DocumentsContract.createDocument()` needs a *document* Uri and throws on most
  providers when handed the tree Uri from `OpenDocumentTree`. Validate the target
  folder once up front, so an unwritable tree fails the run immediately instead
  of being reported as N per-song failures.
- `processed` counts *finished* songs, so the "X of Y" label needs
  `(processed + 1).coerceAtMost(total)` or the final song reads "13 of 12".
- **Unit tests now actually run.** The repo had eight test files and no workflow
  that ran them, so every assertion in them was decorative. `Unit Tests` in
  `build_pull_request.yml` runs `:app:testGmsMobileUniversalDebugUnitTest` before
  assemble — unit tests compile a fraction of the project, so a broken assertion
  fails in about a minute instead of after a full build — and uploads
  `app/build/reports/tests/` on failure. Keep it before the build step.
- That gate immediately caught a pre-existing broken test:
  `TitleMatchTest.rejectsIdenticalTitleByDifferentArtist` used a 141s candidate
  against a 242s wanted duration, and that 101s gap trips the 15s duration hard
  gate — so `evaluate()` rejected on duration and never compared artists, leaving
  the gate the test is named for untested. Only the fixture was wrong.
- `download/AudioContainerTest.kt` covers `AudioContainer.detect`, which decides
  the extension every exported file gets: the synchsafe ID3 size parse, the
  11-bit MPEG frame sync, `ftyp` at offset 4 rather than 0, and RIFF without
  WAVE (which also fronts AVI).

## Constraints / working notes for future sessions

- Cannot build Android locally in this environment — validate compilation via
  the fork's `Build APKs` CI (`build_debug` job). For a feature branch, pushing
  triggers `Build Pull Request`, which compiles the same code; `gh workflow run`
  is forbidden with the fork token (403), so a push is the only way to start CI.
- Kotlin reports only the **first** unresolved reference per file, so one missing
  import hides the next and costs another full CI round. `isActive`,
  `flow.update`, and similar extensions need explicit imports even though they
  look built in.
- ktlint fails on unsorted or unused imports. Ordering is the IntelliJ layout:
  alphabetical, with `java.*`, `javax.*`, and `kotlin.*` last. Inserting an
  import into a plausible but wrong slot is an easy way to fail a build.
- `build_pull_request.yml` has a `concurrency` group with `cancel-in-progress`,
  so a new push supersedes the previous ~8min build instead of queueing behind
  it. Runs started before that group existed cannot be cancelled retroactively.
- Because CI is the only compile gate and each run is slow, unresolved-reference
  typos are expensive. Before pushing, confirm every new symbol has a definition
  (`grep -rn "fun <name>" --include=*.kt .`) and that each new import resolves.
  Extension receivers hide from naive patterns: `fun QobuzAudioQuality.toFormatId`
  does not match `fun toFormatId`.
- Kotlin trap worth remembering: `MutableStateFlow<Set<T>>.value.add(x)` does not
  resolve to a `MutableStateFlow` extension, because `.value` makes the receiver
  an immutable `Set`.
- When chasing a green verdict, stop editing until the working tree is clean and
  `HEAD` equals `origin/<branch>`; otherwise each follow-up commit cancels the
  run you are waiting on.
- GPL-3.0 copyright notices (`© Rukamori — github.com/rukamori`) at the top of
  source files MUST be preserved (per GPL-3.0 Section 4 & 5).
- The working checkout at `.forks/at-fork` is periodically reset; re-clone from
  `vossgraves/ArchiveTune` with `--recurse-submodules` when it goes missing.
