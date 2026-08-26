# AGENTS.md — ArchiveTune (vossgraves fork of 4nx3b/ArchiveTune)

Active development on `dev`, tracking `4nx3b/ArchiveTune@dev`. Any agent working
here must preserve the invariants below.

## Fork invariants — never break

- **Tidal source**: `app/src/main/kotlin/moe/rukamori/archivetune/tidal/` (`TidalAudioProvider`, `TidalAccountManager`, `TidalInstanceHealthManager`, `TidalDns`, `TidalArtworkProvider`), Tidal settings/login UI, `utils/tidal/`; instance racing documented in `INSTANCE_RACING.md`. Live progressive-DASH streams use the `tidal-dash://` scheme routed in `MusicService`.
- **Multi-source audio**: providers live in top-level packages `tidal/`, `deezer/` (DeezerCrypto + Media3 decrypting DataSource), `qobuz/` (+ `QobuzBackupProvider` via the kouzu.in mirror), `spotify/`; shared contract (`DirectStream`, `TitleMatch`, source priority) in `audiosource/`. Playback resolution goes through `resolveMultiSourceDataSpec` in `playback/MusicService.kt`; YouTube is the final fallback. Do not rewire playback around this.
- **Spotify is catalog-only**: `spotifycore/` + app `spotify/` provide metadata, artwork, search, playlist import and track-to-YouTube identification. Spotify is never an `AudioSourceType`.
- **Playback client policy**: `AutoChoosePlaybackClientKey` + `YTPlayerUtils` own bounded YouTube client selection/fallback (incl. video-scoped PO tokens and the yt-dlp layer). Manual mode must remain available.
- **Telegram streaming**: `telegram/` (TDLib wrapper + `TelegramDataSource`), browse/settings/login UI. Playback routed by the `telegram://` branch in `MusicService`'s `SchemeRoutingDataSource`, independent of the multi-source chain. Channels materialise as local playlists (`LPtg<chatId>`); artwork via the Coil `tgart://` fetcher. api_id/hash via `BuildConfig` with public Telegram Desktop fallback.
- **CI signing**: release/nightly and release-shaped CI builds sign with the existing (historical) release keystore via GitHub Secrets `KEYSTORE`/`KEY_ALIAS`/`KEYSTORE_PASSWORD`/`KEY_PASSWORD` — same signing identity as all prior builds, so updates keep installing in place. No workflow may fall back to a committed `app/persistent-debug.keystore`; the file was removed from the tree and must not be restored (the key material remains public in git history — accepted residual risk, maintainer decision 2026-08-26; do NOT rotate to a fresh key without an explicit user-facing migration announcement, it force-reinstalls every user). Local debug builds use AGP's default debug keystore.
- **Listen Together**: LAN + vivimusic public servers only. No koiverse REST/WS path and no `*.koiverse.cloud` / `raw.githubusercontent.com/koiverse/*` call anywhere in the tree.
- **Protected files**: `applicationId` (`moe.rukamori.archivetune`) is fork identity — never adopt upstream changes to it. `Koiverse.jks*`, `ArchiveTuneKoiverseServer.txt`, `DataServer.txt` were removed and must never be restored.

## Modules & submodules

- Gradle modules: `:app :core :spotifycore :canvas :jiosaavn :lastfm :musixmatch :shazamkit :morideobfuscator :lyrics:*`.
- Submodules: `core` → **4nx3b/core** (NewPipeExtractor-based InnerTube client), `lyrics` → **4nx3b/lyrics**, `IconPack` → rukamori, `morideobfuscator` → rukamori. Commit + push inside a submodule first, then pin the gitlink; never leave a dirty or unpushed pointer.

## Build & test

- JDK 21, Android SDK (compileSdk 37), Gradle wrapper 9.6.1.
- Flavor matrix: gms/foss × mobile/tv × universal/arm64/x86_64. Debug check: `./gradlew assembleGmsMobileUniversalDebug`.
- Fork contract tests (palette/crossfade/artwork/multi-source/presence/telegram — sources in `app/src/test/`): `./gradlew :app:testGmsMobileUniversalDebugUnitTest`. Run after any merge or feature change.
- Keep the GPL header banner on new `.kt` files. No formatter task — match surrounding style.

## Dependency gotchas

- `settings.gradle.kts` declares a GCS mirror of Maven Central **before** `mavenCentral()` — do not reorder (Maven Central 429-rate-limits CI).
- JitPack is scoped via `exclusiveContent` to an allow-list of `com.github.*` groups (TeamNewPipe, tdlibx, PRDownloader, jaudiotagger, MetrolistGroup…). A new `com.github.*` dependency fails until its group is added.
- The embedded Python/yt-dlp layer (Chaquopy) was removed on 2026-08-26: YouTube stream resolution is native-only via the compiled InnerTube core (`NativeStreamRepository` + BotGuard/QuickJS PO tokens). Upstream still carries Chaquopy — expect merge conflicts in `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `playback/stream/*` on sync; resolve them by keeping the fork's native-only shape.

## Automated upstream sync (state as of 2026-08-26 — verify before relying on it)

- `upstream-sync.yml` (hourly rukamori/dev merge via `scripts/upstream_sync.sh` + `scripts/ai_resolve.py`, enforcing the invariants in this file) is **inert**: GitHub runs scheduled workflows only from the default branch, and it was removed from `main` on 2026-08-09 after failing for two days (the rukamori line is yt-dlp-first and 5000+ commits diverged).
- `mirror-4nx3b.yml` on `main` "owns dev" by tree-replacing it with 4nx3b stable releases, but has failed 100/100 runs (gitlink bug: its restore loop `rm -rf`s the submodule dirs, so `git add -A` deletes the staged gitlinks). **Do not just fix that bug**: a working tree-replacement mirror would re-commit `app/persistent-debug.keystore` (still present in 4nx3b's tree), resurrect main's committed-keystore signing fallback, and wipe every deliberate fork divergence (yt-dlp removal, keystore removal, UI rework). Direction decision (merge-based 4nx3b sync vs release-tag mirroring only) is pending with the maintainer.
- If you change a fork feature or protected file, update the invariants in **both** this file and `scripts/upstream_sync.sh`'s verification section, so whichever automation ends up owning sync enforces them.

## Agent hygiene

- Never commit credentials, tokens, keystores or agent session state (`.swarm/`, `.opencode/`, `.jcode/` stay untracked).
- Local working notes (`ARCHIVETUNE_SESSION_NOTES.md`, `ARCHIVETUNE_CODE_MAP.md`) are scratch memory — never ship them.
