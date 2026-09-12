# A/V sync in the video-artwork player

`VideoArtworkPlayer` runs a **second** ExoPlayer for the video surface while the service's audio
player stays authoritative for the clock. Keeping the two in step is the whole problem this file
solves, and the constants at the top of it are the tuning. This is why they are what they are.

## The correction ladder

Drift is polled every 250ms (`VideoSyncPollIntervalMs`) and corrected by the gentlest mechanism
that will work:

| Drift | Response | Constant |
|---|---|---|
| under 60ms | nothing | `VideoSyncIgnoreToleranceMs` |
| 60ms – 2000ms | adjust the *video* player's playback speed by up to ±20% to close the gap | `VideoSyncSpeedCorrectionFactorMax` |
| over 2000ms | soft seek: re-anchor the video to the audio position | `VideoSoftSeekDriftThresholdMs` |

60ms comes from ITU-R BT.1359-1 perception thresholds: ~50ms is imperceptible, ~100ms is
noticeable to trained viewers, ~200ms to casual ones, and beyond ~400ms it is annoying. 60ms sits
safely inside the imperceptible band.

The tolerance used to be **2000ms**, and that is precisely why desync was perceptible: drifts of
300–1500ms are routine with VP9/AV1 hardware-decoder lag and were silently ignored. Only a
pause/resume or a quality change resynced anything.

## Why speed correction rather than seeking

A re-anchor seek re-buffers, which the user sees as a 1–2 second stall. Proportional speed
adjustment closes the same gap with no stall at all — the approach MPV (`video-sync=audio`) and VLC
both use by default. The factor is recomputed each poll, so it narrows continuously as drift
approaches zero and resets to the user's chosen playback speed once inside the ignore band.

`VideoSoftSeekDriftThresholdMs` was briefly **400ms**, and that was a regression: the video
decoder warms up slightly behind the audio, so a 400ms threshold fired repeatedly during the first
seconds of playback, re-buffering each time — the "lags for the initial seconds after first play"
report. 2000ms keeps seek-based correction for genuine large drift while never firing during
normal warm-up.

## "Laggy video" is usually not a position error

A frozen or stale presentation on the surface reports a *correct* position. Seeking does not fix
it. That case is handled separately by `VideoArtworkState.kickRenderer()`, a video-only
pause/resume micro-cycle, used by the surface re-attach path and by the frozen-renderer detector:

- `VideoFrozenRendererCycles` consecutive polls where the video position fails to advance,
- while the audio position does advance,
- with "fails to advance" meaning under `VideoFrozenRendererMaxAdvanceMs` of movement per cycle.

A renderer that is presenting frames advances by roughly the poll interval each cycle; a frozen one
barely moves. `SurfaceReanchorMinIntervalMs` rate-limits the kick so a persistently unhappy
renderer cannot be cycled continuously.

`VideoSeekSettlingTimeMs` suppresses drift checks after any seek — soft seek, manual resync, or the
initial snap in `onRenderedFirstFrame` — so a correction is never measured mid-flight and
double-corrected.

`VideoInitialSyncToleranceMs` (200ms) is deliberately looser than the steady-state tolerance: it
applies only to the one-shot snap when the first frame renders, where a redundant seek costs more
than 200ms of imperceptible offset.
