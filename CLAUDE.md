# ArchiveTune development guide

ArchiveTune is a forked Kotlin/Compose Android player. Read [AGENTS.md](AGENTS.md) before changing code; its fork invariants are authoritative.

## Build and test

The project uses JDK 21, Android compileSdk 37, and Gradle 9.6.1.

```bash
./gradlew assembleGmsMobileUniversalDebug
./gradlew :app:testGmsMobileUniversalDebugUnitTest
```

The Canary integration target is `canary`. Device QA must finish before changes are merged into `dev`. CI is the authoritative build path when a local Gradle daemon cannot complete.

## Architecture rules

- Tidal, Deezer, Qobuz, Telegram, and YouTube remain audio providers. Spotify is catalog, library, search, playlist, and history metadata; it is not an `AudioSourceType`.
- Playback resolution goes through `resolveMultiSourceDataSpec` and `MusicService` scheme routing. Do not bypass the multi-source chain.
- Native YouTube stream resolution remains in the fork. Do not restore Chaquopy, yt-dlp, upstream signing fallbacks, koiverse endpoints, or removed keystore files.
- Submodules must be committed and pushed before their gitlinks are updated.
- New Kotlin files keep the GPL header. Comments should explain non-obvious decisions only; do not add narration or stale TODOs.

## Durable handoff

The maintained task state is in [docs/claude/](docs/claude/):

- [HANDOFF.md](docs/claude/HANDOFF.md) — current branch, PR, verification, and device-QA state.
- [ARCHITECTURE.md](docs/claude/ARCHITECTURE.md) — protected playback, source, and lifecycle boundaries.
- [SETTINGS.md](docs/claude/SETTINGS.md) — settings routes, search anchors, and Apple Music surface status.
- [RELEASES.md](docs/claude/RELEASES.md) — Canary/Nightly build and installation procedure.
- [BACKLOG.md](docs/claude/BACKLOG.md) — prioritized follow-up work and known gaps.

Existing subsystem references remain useful: [docs/extraction.md](docs/extraction.md), [docs/lyrics.md](docs/lyrics.md), [docs/sponsorblock.md](docs/sponsorblock.md), [docs/tv.md](docs/tv.md), and [docs/spotify-native-playback.md](docs/spotify-native-playback.md).

## Review discipline

Keep changes reviewable and preserve tests under `app/src/test` and `lastfm/src/test`; they protect fork contracts. Review `git diff --check`, run the focused unit tests, inspect the affected routes in a managed Preview where applicable, and use Canary CI plus a physical-device pass for playback, thermal, and release validation.

Do not merge into `dev` before the device-QA checklist in [docs/claude/RELEASES.md](docs/claude/RELEASES.md) is complete.
