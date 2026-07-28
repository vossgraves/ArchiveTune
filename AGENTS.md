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
5. **Protected files** — `Koiverse.jks`, `Koiverse.jks.base64`,
   `ArchiveTuneKoiverseServer.txt`, `DataServer.txt`, and the `applicationId`
   (`moe.rukamori.archivetune`) in `app/build.gradle.kts` are fork-identity
   files: do not adopt upstream changes to them without explicit instruction.

## Submodules

- `core` → **vossgraves/core** (our fork of rukamori/core). Sync strategy:
  merge `rukamori/core` into `vossgraves/core` first, then pin the gitlink to
  the merged commit — never blindly adopt upstream's gitlink.
- `lyrics`, `moriextractor`, `IconPack`, `morideobfuscator` → rukamori-owned;
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

`build_pull_request.yml` runs that same task before assembling, so a broken
assertion fails in seconds rather than after a full build. Keep it that way: for
a long time these tests existed but no workflow ran them, which made every
assertion in them decorative.

## Working without a local JDK

If you are editing this repo from an environment with no JDK/Android SDK, you
cannot compile, and CI is the only real verdict. That makes cheap pre-push
checks worth far more than usual:

- **Verify every symbol resolves to an import.** Kotlin reports only the *first*
  unresolved reference per file, so one missing import hides the next. After
  adding code, list the external symbols you used and confirm each is imported.
  Same-package types need no import; extensions like `isActive`, `flow.update`,
  and `dataStore.get` do.
- **Keep imports sorted, with no unused entries.** ktlint fails the build on
  either. Ordering is the IntelliJ layout: everything alphabetically, with
  `java.*`, `javax.*`, and `kotlin.*` last. Inserting an import in a plausible
  but wrong slot is the single most common way to fail a build here.
- **Do not push while a build is in flight.** `build_pull_request.yml` sets
  `cancel-in-progress`, so pushing again cancels the run you were waiting on and
  you never get a verdict. Batch fixes, then push once.
- Read compile errors straight out of the log:
  `gh run view <id> --log-failed | grep -oE "e: file:///[^ ]*\.kt:[0-9]+:[0-9]+ .*"`

## Coroutine scopes for work that outlives the UI

Downloads and exports must not run on a composition or ViewModel scope. Both die
when the user navigates away, which cancels the transfer partway and leaves
partial files behind. Own a process-lived scope
(`CoroutineScope(SupervisorJob() + Dispatchers.IO)`) in a singleton — see
`download/LosslessDownloader.kt` and `download/CacheExporter.kt` — and expose
progress as a `StateFlow` so a screen reopened mid-run rebinds to the work
already in flight instead of starting a second copy.

For single-flight guards, use `MutableStateFlow.compareAndSet` in an explicit
loop, **not** `update {}`: `update` re-runs its lambda after losing a race, so a
flag captured inside it can report success for an attempt that never took
effect. Note also that `stateFlow.value.add(x)` does not compile when the value
is an immutable collection.
