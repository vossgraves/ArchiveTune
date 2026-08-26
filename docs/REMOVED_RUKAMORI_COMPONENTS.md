# Removed Rukamori/Koiverse Components — Reference

This file documents components deleted during the 2026-08 koiverse cleanup so
nothing functionally important is lost. **Every file below is still recoverable
from git history** at commit `819476ff6` (and earlier):

```bash
git show 819476ff6:<path>     # print a deleted file
git checkout 819476ff6 -- <path>   # restore it into the tree
```

Do **not** restore any of these without re-evaluating: they were removed
because they phone home to `*.koiiverse.cloud` / Rukamori servers, or were
inert leftovers. The AGENTS.md + `scripts/upstream_sync.sh` Phase 4 guards
will fail the build/sync if the koiverse surface is reintroduced.

---

## 1. Listen Together — koiverse REST/WS path (deleted)

Files: `app/src/main/kotlin/moe/rukamori/archivetune/together/TogetherOnlineApi.kt`,
`TogetherOnlineEndpoint.kt`, `TogetherOnlineHost.kt`, root `ArchiveTuneKoiverseServer.txt`,
plus the `MusicTogetherConnectionMode.ONLINE` branches in `MusicTogetherRepository.kt`
and `MusicTogetherViewModel.kt`, `startTogetherOnlineHost`/`joinTogetherOnline`/
`togetherOnlineErrorMessage` in `playback/MusicService.kt`, the remote
`connect(wsUrl, sessionId, sessionKey, displayName)` overload in `TogetherClient.kt`,
and `TogetherOnlineEndpointCacheKey`/`TogetherOnlineEndpointLastCheckedAtKey` in
`constants/PreferenceKeys.kt`.

Why: this was Rukamori's private session server. The app fetched the base URL
from `https://raw.githubusercontent.com/4nx3b/ArchiveTune/refs/heads/dev/ArchiveTuneKoiverseServer.txt`
(which contained `https://archivetune-api.koiiverse.cloud`), created sessions via
REST, and joined via `wss://<host>/v1/together/ws`.

Wire format (if a compatible server is ever needed again):

- `POST {base}/v1/together/sessions` — body `{hostDisplayName, settings}`,
  returns `{sessionId, code, hostKey, guestKey, wsUrl, settings}`.
- `POST {base}/v1/together/sessions/resolve` — body `{code}`,
  returns `{sessionId, guestKey, wsUrl, settings}`.
- `POST {base}/v1/together/sessions/{id}/end` — bearer `hostKey`.
- WS protocol: `ClientHello{protocolVersion, sessionId, sessionKey, clientId,
  displayName}` → `ServerWelcome{protocolVersion, sessionId, participantId,
  role, isPending, settings}`; then `room_state`, `control_request`,
  `add_track_request`, `join_decision`, `join_request`, `participant_joined`,
  `participant_left`, `host_transfer`, `heartbeat_ping/pong`, `client_leave`,
  `kick`, `ban` (see `TogetherMessages.kt`, still in the tree — the message
  envelope survived for the LAN server).

The live replacement is the vivimusic public protocol (`TogetherPublicServers.kt`
defaults `wss://devilmi-vivi-music-listen-together.hf.space` /
`wss://vivimusic-listen-together.onrender.com`, `TogetherPublicClient.kt`,
`TogetherPublicProtocol.kt`), plus the LAN `TogetherServer.kt`/`TogetherClient.kt`.

## 2. Canvas proxy — ArchiveTuneCanvas (deleted)

File: `canvas/src/main/kotlin/moe/rukamori/archivetune/canvas/ArchiveTuneCanvas.kt`.

Why: it proxied artwork through `https://artwork-archivetune.koiiverse.cloud/`
with fallback `https://artwork.boidu.dev/` and attached a bearer token.

API shape (all delegated to Apple Music internally): `getBySongArtist(song,
artist, storefront, forceRefresh, strict, album)`, `getByAlbumId(albumId,
storefront)`, `initialize(bearerToken)`.

Replacement: `AppleMusicProvider` (same module) already implemented the
underlying Apple Music motion-artwork calls, so `CanvasArtworkResolver.kt` and
`AlbumViewModel.kt` now call `AppleMusicProvider` directly. The
`PreferredArtworkProvider.ARCHIVETUNE_CANVAS` enum value and
`ArchiveTuneCanvasKey` preference were **kept** (the "ArchiveTune Canvas"
setting now means Apple Music motion artwork); only the network proxy class was
removed. Note the AppleMusic path has no `strict` identity parameter — the
caller's `strictIdentity` filtering still applies to the returned artwork.

## 3. Inert cipher UI (deleted; the engine module was kept)

Files: `app/src/main/kotlin/moe/rukamori/archivetune/cipher/CipherSettingsRepository.kt`,
`CipherSettingsUseCases.kt`, `ui/screens/settings/ChiperSettings.kt`,
`viewmodels/ChiperSettingsViewModel.kt`, `utils/MoriCipherUpdateWorker.kt`,
`MoriCipherManualRefreshHistoryKey` (PreferenceKeys), 25 `mori_cipher_*` strings
(all locales), the `settings/player/chiper` nav route + settings entry.

Why: the app never called `MoriCipherRuntime.initialize(...)` and never
scheduled `MoriCipherUpdateScheduler`, so the whole screen/worker was inert
dead code. The YouTube cipher engine itself — the `morideobfuscator` submodule
(user's fork) — was **kept untouched**.

If the cipher UI is ever revived, the concepts to rebuild: a settings screen
showing engine status (`CipherRuntimeStatus`), last/next refresh times, manual
refresh with a rate limit (3 manual refreshes, backoff), automatic refresh
every 6 h + 30 min via `PeriodicWorkRequestBuilder` constrained to
network-connected + battery-not-low, and the DataStore history key
`moriCipherManualRefreshHistory`. Strings: `mori_cipher_settings_title`,
`mori_cipher_status*`, `mori_cipher_refresh*`, `mori_cipher_rate_limit*`,
`mori_cipher_automatic_updates*`, `mori_cipher_last_updated`,
`mori_cipher_next_refresh`, `mori_cipher_player_id`,
`mori_cipher_loading_configuration`, `mori_cipher_empty_*`,
`mori_cipher_error_title`, `mori_cipher_load_failed`.

## 4. Gatekeeper stub (deleted)

File: `app/src/main/kotlin/moe/rukamori/archivetune/viewmodels/GatekeeperViewModel.kt`
plus its `MainActivity.kt` wiring. It was already a stub ("the gate is always
open", `blockedMessages` never emits) from a prior cleanup; removing it just
deleted the dead wiring.

## 5. moriextractor submodule (deleted)

Submodule `https://github.com/rukamori/moriextractor` (dual license:
GPL-3.0-only or commercial, © 2026 morieeattonkatsu). Contents (commit
7abc1d7): `StreamingExtractionManager` with `ExtractorAuthenticationCallback`,
`BearerTokenRepository`/`InMemoryBearerTokenRepository`, `ExtractedAudio`
(`streamUrl`, `streamExpiresAt`), `StreamStatusResponse` (`stream_id`,
`stream_expires_at`), `BackendExtractorResponse`. This was Rukamori's
server-side stream extraction client.

Why: **zero** references anywhere in `app/src` or `core/src` (verified) — a
phantom dependency left after the playback stack moved to
`MetrolistExtractor` (declared in `core/build.gradle.kts` via
`libs.metrolist.extractor`). Removed: the `settings.gradle.kts` include,
`app/build.gradle.kts` implementation, the proguard keep rule, the
`.gitmodules` entry, and the `scripts/upstream_sync.sh` / `AGENTS.md` lists.

## 6. Keystores and junk (deleted, do NOT restore)

- `Koiverse.jks` + `Koiverse.jks.base64` — Rukamori's committed signing
  keystore. **Deliberately not preserved**: a committed keystore is a
  compromised credential. If a release signing key is ever needed, generate a
  new one and store it in the GitHub `KEYSTORE`/`KEY_ALIAS`/
  `KEYSTORE_PASSWORD`/`KEY_PASSWORD` secrets; the committed
  `app/persistent-debug.keystore` fallback has since been removed as well
  (release-shaped CI fails closed without the secrets).
- `DataServer.txt` (`https://archivetune-data.koiiverse.cloud`) — fed the
  unused `DATA_SERVER_URL` BuildConfig field.
- `ArchiveTuneKoiverseServer.txt` (`https://archivetune-api.koiiverse.cloud`) —
  Listen Together endpoint source (see §1).
- `div`, `div.blyrics--active` — 0-byte accidents at repo root.

## 7. app/build.gradle.kts — removed config knobs

- `DATA_SERVER_URL` (read from `DataServer.txt`, fallback
  `archive-tune-admin-remote.vercel.app`) and `API_BEARER_TOKEN` — zero code
  references; deleted along with the `asBuildConfigString()` helper.
- `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER` — zero
  code references after the above removals.
- **Kept on purpose:** `SOURCE_PROVIDER_URL` (default
  `https://archivepool.vercel.app`), `SOURCE_PROVIDER_KEY`, `POOL_CLIENT_KEY` —
  these feed the fork's own Tidal/Qobuz instance discovery
  (`TidalAudioProvider`, `QobuzAudioProvider`, `PoolAccountManager`) and are
  part of the multi-source playback invariant, not Rukamori phone-home.
