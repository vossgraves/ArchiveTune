# Lyrics

## Parsing

One parser for everyone: `lyrics/LyricsUtils` (`parseLyrics`, `parseTtml`,
`isLineSyncedLrc`, `isTtml`, `findCurrentLineIndex`, `insertInstrumentalBreaks`).
A format either works in every renderer or in none. Providers live in `lyrics/`
and are selected in Lyrics settings.

Word-level timing only exists where the provider supplies it (TTML and the
rich-sync providers). Everything else is line-synced, and a renderer must degrade
to whole-line highlighting rather than pretending.

## Renderers

| Renderer | Where |
|---|---|
| `ui/component/LyricsEnhanced.kt` | The default full-screen renderer; also drawn inside SimpMusic's lyrics card for every mode except SIMPMUSIC |
| `ui/component/LyricsV2.kt` | Alternate full-screen renderer |
| `ui/player/simpmusic/SimpMusicLyrics.kt` | The SIMPMUSIC lyrics *mode* — a user-selectable style, reachable from the full-screen surface and the card |

`LyricsMode` (settings) picks between these. Do not delete `SimpMusicLyrics`
thinking it is player-style chrome — it is a lyrics style a user can select.

## The two compact surfaces

Both players carry a small "current line" strip, and they are *not* the same shape:

- **Bitchord**: `CurrentLyricLine` in `ui/player/bitchord/BitChordLyrics.kt` — one
  strip directly above the scrubber, with a chevron into the full lyrics page.
- **SimpMusic**: `SimpMusicLyricLine` in `ui/player/simpmusic/SimpMusicPlayer.kt` —
  a 48dp centred band between the artwork and the title row. Its height feeds the
  artwork sizing arithmetic in the hero layout, so it is fixed on purpose.

SimpMusic *also* has a 300dp lyrics card further down the page
(`SimpMusicLyricsCard`), below the fold with the artist and track-info cards. That
is a different surface again, and it renders `LyricsEnhanced`.

## The word sweep

`SweptLyricLine` draws a line as two stacked copies — dim underneath, bright on top
clipped to however much has been sung — plus an optional blurred third copy for the
glow. The clip is recomputed in the draw phase, so a frame costs a clip and a redraw
rather than a recomposition.

It is fed by `rememberLyricClock`, which carries the player's twice-a-second position
report forward on the frame clock so the sweep does not step.

**Both compact surfaces share this renderer.** That is a deliberate exception to the
player-style self-containment rule (2026-09-01, amended 2026-09-07): SimpMusic's band
used to highlight whole lines off the same word timings Bitchord swept through, and
a second copy of the sweep is a second thing to fix whenever a provider changes shape.
Everything else in each style stays its own.

## Two things that were wrong, so they do not come back

- **Clock gating.** The sweep must be driven by `PlayerConnection.isAudioAdvancing`
  (Media3's real `isPlaying`), never `PlayerConnection.isPlaying` — the latter is
  `playWhenReady && state != ENDED`, which stays true right through a buffer and
  through focus suppression, so lyrics ran on while nothing was audible. `isPlaying`
  stays as it is for play/pause affordances, which should flip on tap.
- **Truncation.** A one-line strip with `TextOverflow.Ellipsis` ended most lyrics in
  a "…". The strips reserve two lines and hold that height whether they fill it or
  not; growing to fit would move the controls underneath every time the line changed.
