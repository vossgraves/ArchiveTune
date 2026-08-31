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
- **Signing (HARDENED 2026-08-26, supersedes everything below):** the committed
  `app/persistent-debug.keystore` was removed from the tree (a committed key is
  a supply-chain risk — anyone could sign a malicious "update"). The SAME key
  (alias `androiddebugkey`, creds `android`/`android`, cert SHA-256
  6D:B8:32:6A:…) now lives ONLY as GitHub Secrets on vossgraves/ArchiveTune
  (`KEYSTORE`/`KEY_ALIAS`/`KEYSTORE_PASSWORD`/`KEY_PASSWORD`), so existing
  users keep updating in place — maintainer decision 2026-08-26 to NOT rotate
  (rotation would force-reinstall every user; the key being public in git
  history is accepted residual risk). Release/nightly/build CI fails closed
  when any secret is missing; no committed fallback may ever return (guard
  lives in `scripts/upstream_sync.sh` + AGENTS.md). Local debug builds use
  AGP's default debug keystore. Historical context, now obsolete:
- Debug builds used to sign with the committed `persistent-debug.keystore`
  for a stable signature (no forced uninstall/reinstall between builds).
- **Release APK signing (stable signature across builds/ABIs, OBSOLETE):** CI
  previously ran `keytool -genkeypair` to create a fresh EPHEMERAL keystore
  whenever no `KEYSTORE` secret was set (the fork case). Because each ABI
  variant (arm64, x86_64, universal, TV, FOSS) is a separate matrix job, even
  variants from the SAME run got different signatures → Android refused
  in-place updates → forced uninstall+reinstall. That was "fixed" at the
  time by signing with the committed keystore — the 2026-08-26 hardening
  moved that same key into secrets instead. Upstream's real-`KEYSTORE`-secret
  path is unchanged; PR builds (lint-only) were left untouched.
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

## Constraints / working notes for future sessions

- Cannot build Android locally in this environment — validate compilation via
  the fork's `Build APKs` CI (`build_debug` job).
- GPL-3.0 copyright notices (`© Rukamori — github.com/rukamori`) at the top of
  source files MUST be preserved (per GPL-3.0 Section 4 & 5).
- The working checkout at `.forks/at-fork` is periodically reset; re-clone from
  `vossgraves/ArchiveTune` with `--recurse-submodules` when it goes missing.

## 2026-08: koiverse phone-home removal

Session 2026-08-10 removed the koiverse/archivetune phone-home surface:
delete `Koiverse.jks`/`.base64`, `DataServer.txt`, `ArchiveTuneKoiverseServer.txt`,
drop the `moriextractor` submodule, remove the inert cipher UI (keep
`morideobfuscator`), repoint About/donate/support links to
`github.com/4nx3b/ArchiveTune`, make News a local placeholder, and cut the
koiverse Listen-Together REST/WS path so public rooms use vivimusic's WSS
servers via `TogetherPublicClient`. The canvas proxy was replaced by
Apple Music / Spotify providers. See `.jcode/PLAN_OPUS.md` for the full plan.

## 2026-08: public Listen Together moved to the Metrolist protobuf server

Session 2026-08-31 replaced the dead vivimusic JSON servers with the
Metrolist community server (`wss://metroserverx.meowery.eu/ws`, "The
Meowery") so ArchiveTune, Metrolist and SimpMusic share rooms on one
protocol, still auth-free. The wire layer is
`together/TogetherPublicProto.kt` (protobuf Envelope, gzip above 100
bytes, `encodeDefaults=false` — proto3 semantics; `@ProtoNumber`s are
MetrolistGroup/metroproto's `listentogether.proto`, don't renumber).
App-facing models in `TogetherPublicProtocol.kt` are unchanged;
`TogetherPublicClient.kt` translates.

Non-obvious server semantics encoded in the client (each has a comment
at its site):

- `client_capabilities` must be the FIRST frame or the server answers
  `unsupported_client` and no room is ever joined. The type strings are
  NOT in the .proto — they come from metroserver's protocol.go.
- The server's room queue EXCLUDES the current track
  (`sanitizeUpcomingQueue`); the client prepends it back so guest queue
  indices aren't off by one. Idempotent for full queues.
- PLAY before any CHANGE_TRACK is rejected (`no_track`); the host's
  diff broadcast therefore sends CHANGE_TRACK first, then an explicit
  PLAY or PAUSE (the server forces IsPlaying=false on change_track, so
  a paused host skipping tracks would otherwise leave guests playing).
- Guests cannot send `playback_action` at all (`not_host`, quietly
  dropped). Guest add-track goes `suggest_track` → host auto-approves
  (SuggestionReceived branch) → server broadcasts queue_add to everyone.
- `buffer_ready` is the catch-up mechanism: the server answers with a
  precise seek+play/pause pair. Guests send it on join, rejoin and every
  change_track relay.
- Session-expired error codes clear the stored token and replay the
  pending create/join instead of dead-ending; join-failure error codes
  surface as JoinRejected.

Same session: ported upstream rukamori's IP-rotation refresh after
bot detection into `YTPlayerUtils.resolvePlaybackData` (the fork's
playback/stream package is fork-only and already exceeds upstream —
3-attempt recovery tracker, codec-state recovery, offline cache bypass,
URL probe failsafe — so only this one piece was worth porting).
