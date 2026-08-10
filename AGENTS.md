# AGENTS.md — vossgraves/ArchiveTune (fork)

This is a fork of [rukamori/ArchiveTune](https://github.com/rukamori/ArchiveTune)
with substantial custom features. Any agent working in this repo MUST preserve
the fork invariants below — especially when merging upstream changes.

## Fork invariants (never break these)

1. **Tidal music source** — `app/src/main/kotlin/moe/rukamori/archivetune/tidal/`
   (`TidalAudioProvider`, `TidalInstanceHealthManager`, `TidalAccountManager`,
   `TidalDns`, `TidalArtworkProvider`), settings UI
   `ui/screens/settings/TidalSettings.kt` + `TidalLoginScreen.kt`,
   `utils/tidal/TidalCookieUtils.kt`. Concurrent instance racing documented in
   `INSTANCE_RACING.md`.
2. **Multi-source audio framework** —
   `app/src/main/kotlin/moe/rukamori/archivetune/audiosource/` (Deezer/Amazon
   providers, ISRC resolver, source priority config). Playback resolution goes
   through `resolveMultiSourceDataSpec` in
   `playback/MusicService.kt` — YouTube is the final fallback. Do not rewire
   playback around this.
3. **Telegram channel streaming** —
   `app/src/main/kotlin/moe/rukamori/archivetune/telegram/` (`TelegramClient`
   TDLib wrapper, `TelegramDataSource` Media3 streaming source, media-id codec
   + models), UI `ui/screens/TelegramBrowseScreen.kt` +
   `ui/screens/settings/TelegramSettings.kt` + `TelegramLoginScreen.kt`.
   Playback is routed by the `telegram://` scheme branch in `MusicService`'s
   `SchemeRoutingDataSource` (independent of the multi-source resolver chain).
   Depends on the prebuilt TDLib AAR `com.github.tdlibx:td` (JitPack group
   allow-listed in `settings.gradle.kts`). Login is phone + code only — the
   app's api_id/api_hash are baked in via `BuildConfig.TELEGRAM_API_ID`/`_HASH`
   (`buildConfigField` in `app/build.gradle.kts`, overridable through
   local.properties / env, with the public Telegram Desktop credentials as the
   fallback); users never enter developer credentials. Opening a channel
   **materialises it into a real local playlist** (`TelegramChannelSync`,
   deterministic id `LPtg<chatId>`) so it reuses the normal playlist UI; there
   is no bespoke channel screen. Artwork resolves lazily through the Coil
   `tgart://` fetcher (`TelegramThumbnailFetcher`) — HQ catalogue cover by
   title/artist (`TelegramCoverProvider`, iTunes), falling back to the embedded
   Telegram cover. Downloads route `telegram://` through TDLib via
   `DownloadUtil`'s `DownloadSchemeRoutingDataSource`.
4. **Fork CI signing patch** — workflows sign with the committed
   `app/persistent-debug.keystore` when the `KEYSTORE` secret is absent
   (forks have no release keystore). Upstream's workflows must not overwrite
   this logic (see `build.yml`, `release.yml`).
5. **Listen Together runs on LAN + the vivimusic public servers only** —
   there is no koiverse REST/WS path and no `*.koiiverse.cloud` or
   `raw.githubusercontent.com/koiverse/*` network call anywhere in the tree.
   Public rooms connect via `TogetherPublicServers` (vivimusic WSS endpoints)
   and `TogetherPublicClient`; LAN rooms via `TogetherServer`/`TogetherClient`.
6. **Protected files** — the `applicationId` (`moe.rukamori.archivetune`) in
   `app/build.gradle.kts` is a fork-identity file: do not adopt upstream
   changes to it without explicit instruction. (`Koiverse.jks`,
   `Koiverse.jks.base64`, `ArchiveTuneKoiverseServer.txt` and `DataServer.txt`
   were removed as part of the koiverse phone-home cleanup and must never be
   restored.)

## Submodules

- `core` → **vossgraves/core** (our fork of rukamori/core). Sync strategy:
  merge `rukamori/core` into `vossgraves/core` first, then pin the gitlink to
  the merged commit — never blindly adopt upstream's gitlink.
- `lyrics`, `IconPack`, `morideobfuscator` → rukamori-owned;
  always adopt upstream's recorded pointers.

## Automated upstream sync

`.github/workflows/upstream-sync.yml` runs hourly and merges
`rukamori/ArchiveTune@dev` into `dev`, using `scripts/upstream_sync.sh` +
`scripts/ai_resolve.py` (LLM conflict resolution following the invariants
above). Full documentation: `docs/UPSTREAM_SYNC.md`. If you modify fork
features or protected files, update the invariants in **both** this file and
the verification section of `scripts/upstream_sync.sh`.

## Build

JDK 21, Android SDK. Debug build check:
`./gradlew assembleGmsMobileUniversalDebug`

Fork contract tests (palette/crossfade/artwork/multi-source/presence — run
after any upstream merge or feature change):
`./gradlew :app:testGmsMobileUniversalDebugUnitTest`
