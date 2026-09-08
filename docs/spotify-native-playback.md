# Spotify-native playback (catalogue tracks with no YouTube release)

Status: **design only — not implemented.** Phase 1 (exact-ISRC resolution for tracks
that *do* get a YouTube id) shipped; this document specifies Phase 2 and the reasons it
was not blind-shipped.

## The problem

Playback in this fork is keyed on a YouTube `mediaId`. A Spotify track enters a queue
through `SpotifyPlaybackResolver.resolveToMetadata`, which searches YouTube Music and
returns a `MediaMetadata` **keyed by the matched YouTube video id**. When no YouTube
match clears the score threshold the resolver returns `null`, and both queue builders
drop it:

- `spotify/SpotifyTracksQueue.kt` — `…resolveToMediaItem(track) … .filterNotNull()`
- `spotify/SpotifyPlaylistQueue.kt` — same

So a track that exists on Spotify/Deezer/Qobuz/Tidal but has **no** YouTube Music
release never reaches the queue at all, even though the multi-source chain could play it
from a lossless source. This is the "the song doesn't play via Spotify" case.

Phase 1 does **not** fix this: it improves resolution accuracy for tracks that already
get a YouTube id (resolving the lossless source by exact ISRC instead of a fuzzy search),
but a YouTube-absent track is still dropped before playback.

## Why the multi-source chain *could* play these today

`playback/MusicService.kt` resolves a non-YouTube source without needing a YouTube id:

- `resolveMultiSourceDataSpec(dataSpec, mediaId, …)` only early-returns for
  `mediaId.isLocalMediaId()` / `mediaId.isTelegramMediaId()`; everything else flows into
  the chain.
- `buildSourceQuery(mediaId)` builds the lookup (title/artist/album/duration/**isrc**)
  from the DB **or** from `queuedMetadataByMediaId` — an in-memory map filled from the
  queue item's tags. A track with no DB row still resolves as long as its metadata is on
  the queue item.

So the missing piece is not the resolver — it is getting a **stable, non-YouTube queue
item** into the queue and making the rest of the app tolerate a media id that is not a
YouTube video id.

## Proposed design

### 1. A Spotify-native media-id scheme

Mirror the Telegram pattern (`telegram/TelegramMediaId.kt`, `String.isTelegramMediaId()`):

- Encode as `sp:{spotifyTrackId}` (or an ISRC-based key when no track id is present).
- Add `utils/…` helpers `String.isSpotifyMediaId()` / encode / decode, matching the
  Telegram helpers exactly in shape.

### 2. Queue builders emit native items for YouTube misses

In `SpotifyPlaybackResolver`, when no YouTube match clears the threshold **but** the
track carries enough identity (ISRC, or title+artist+duration), return a `MediaMetadata`
with:

- `id = sp:{…}` (native id, not a YouTube id),
- `isrc`, `title`, `artists`, `album`, `duration`, `spotifyTrackId` populated,
- `thumbnailUrl` from Spotify.

The queue builders then keep it instead of `filterNotNull`-dropping it.

### 3. Every YouTube-id assumption must guard the new id

This is the expensive part and the reason for not shipping blind. A YouTube media id is
assumed in many places; each must treat `sp:` like local/telegram (skip, or handle
natively). Audit at minimum:

- `MusicService`: metadata fetch (`YTPlayerUtils`/InnerTube), related/radio continuation,
  scrobble id, `resolveMultiSourceDataSpec` (must **not** fall through to YouTube for a
  `sp:` id — there is no video to play), `saveQueueToDisk`/restore.
- Likes / library / history writes keyed on the song table `id` (a `sp:` id must not
  collide with or masquerade as a YouTube id).
- Artwork loading (Coil) — `sp:` needs the Spotify thumbnail path, not a `tgart://`/YT one.
- Downloads (`DownloadUtil`) — either support or explicitly refuse `sp:` items.
- Anything calling `isLocalMediaId()`/`isTelegramMediaId()` today likely needs the `sp:`
  case too; grep both and review each site.

### 4. Fallback when no source has the track

If the chain finds nothing (no pooled Tidal/Qobuz/Deezer account has the recording and it
is not on YouTube), the item is genuinely unplayable. Surface it as skipped-with-reason in
the UI rather than a silent failure or a wrong-song substitution.

## Why this is not shipped yet

- It needs on-device verification with a **populated pool** (an empty pool makes every
  `sp:` item unplayable and indistinguishable from a bug).
- A missed guard site (section 3) can crash playback or corrupt the song table — none of
  which is observable from a headless build. It compiles-clean tests nothing here.

Ship it behind on-device testing, not a green compile.
