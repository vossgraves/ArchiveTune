# Architecture and protected boundaries

## Playback

`MusicService` owns the Media3 player, notification/session lifecycle, queue persistence, audio focus, wake lock, route recovery, source switching, and crossfade. `CrossfadePolicy` is pure decision logic and is unit tested.

The primary player uses a bounded load control and network wake mode. Crossfade uses a separately prepared player only when explicitly enabled. Offload is disabled during crossfade because overlapping players and audio processing are not compatible with the low-power path. Secondary-player promotion is generation guarded; failed promotion leaves the outgoing player authoritative.

When an outgoing player reaches `STATE_ENDED` during an incomplete fade, the service clears `pauseAtEndOfMediaItems` and resumes the next queue item only when playback had been requested and a valid target exists. This prevents the reported second-song stop without forcing playback after an intentional pause.

Stream errors are handled through bounded URL/cache invalidation, source re-resolution, codec recovery, and optional next-item skipping. Do not turn a terminal error into an unbounded retry loop.

## Sources

The shared `audiosource` contract resolves Tidal, Qobuz, Deezer, and YouTube streams. Spotify remains catalog-only. Telegram uses independent `telegram://` routing. Tidal progressive DASH uses `tidal-dash://`. YouTube is the final fallback.

No new provider may bypass `resolveMultiSourceDataSpec`, replace the source priority contract, or introduce a REST/WS path to koiverse domains.

## Performance

SpatialFlow research is recorded in `HANDOFF.md`. Its player is a useful comparison, not a source to copy wholesale. Before changing buffer sizes or adding audio processors, measure transition latency, resolver calls, cache hit rate, decoder state, dropped frames, CPU, radio use, and thermal status. Low-end validation must cover crossfade, canvas/artwork, lyrics, and audio offload independently.

## Data and privacy

Spotify history synchronization is read-only and uses the existing authenticated Recently Played request. It is opt-in, appears in the History source list only when enabled, and never launches Spotify playback or a background WebView.
