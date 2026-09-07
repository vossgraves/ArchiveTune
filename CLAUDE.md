# CLAUDE.md

ArchiveTune — a Kotlin/Compose Android music player. Multi-source: Tidal, Deezer,
Qobuz and Telegram serve audio, Spotify serves catalog metadata only, YouTube is
the final fallback.

**Read [AGENTS.md](AGENTS.md) first.** It carries the fork invariants — protected
files, the removed phone-home surface, signing rules, submodule handling. Nothing
here overrides it.

## Build

JDK 21, compileSdk 37, Gradle wrapper 9.6.1. Flavors: `gms|foss` × `mobile|tv` ×
`universal|arm64|…`.

```
./gradlew assembleGmsMobileUniversalDebug        # compile check
./gradlew :app:testGmsMobileUniversalDebugUnitTest # fork contract tests
```

CI builds `gms-mobile-arm64` and `gms-tv-universal` on every push to `main`/`dev`;
that is the only compile signal if you cannot build locally. There is no `foss`+`tv`
variant, deliberately — the fork ships GMS-only.

## Subsystems

| Doc | What it covers |
|---|---|
| [docs/extraction.md](docs/extraction.md) | YouTube stream resolution: client order, PO tokens, backoff, yt-dlp fallback |
| [docs/lyrics.md](docs/lyrics.md) | The three renderers, the word sweep, which surface uses which |
| [docs/sponsorblock.md](docs/sponsorblock.md) | Segment lookup and skipping |
| [docs/tv.md](docs/tv.md) | Android TV / Fire TV: detection, focus, what is known to be missing |
| [docs/REMOVED_RUKAMORI_COMPONENTS.md](docs/REMOVED_RUKAMORI_COMPONENTS.md) | What the 2026-08 cleanup deleted, and why not to restore it |

## House rules

- Comments explain *why*, not *what*. Most code needs none.
- Player styles are self-contained (`bitchord/`, `simpmusic/`, `tiktok/`). The one
  exception is the swept lyric renderer — see [docs/lyrics.md](docs/lyrics.md).
- New `.kt` files keep the GPL header banner. No formatter task; match the file
  around you.
- Never restore anything in `docs/REMOVED_RUKAMORI_COMPONENTS.md` without reading
  why it went.
