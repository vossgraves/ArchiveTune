# YouTube stream extraction

YouTube is the last source tried; everything here is dead code for a Tidal, Deezer,
Qobuz or Telegram track. See [AGENTS.md](../AGENTS.md) for the multi-source contract.

## The chain

`ResolveAudioStreamUseCase` (`playback/stream/`) is the entry point. It caches by
`(mediaId, quality, metered, purpose, authFingerprint, pinnedFormatId)`, collapses
concurrent requests for the same key into one in-flight resolution, and tries:

1. **`NativeStreamRepository`** — the compiled InnerTube core, via `YTPlayerUtils`.
   Fast, no Python. This is the hot path.
2. **`YtdlnisStreamRepository`** — only after the native path throws. Goes out to an
   external yt-dlp plugin APK through `ytdlp/CompactYtDlp.kt`, the way YTDLnis does.

The embedded Python/Chaquopy yt-dlp layer was removed in 2026-08 and must not come
back; the external plugin above is a different thing and is fine.

## Client selection

`YTPlayerUtils.STREAM_FALLBACK_CLIENTS` is tried in order: `VISIONOS`,
`ANDROID_VR`, `WEB_REMIX`, `WEB`, `MWEB`, `WEB_CREATOR`. Playback asks for
`WEB_REMIX` first (`NativeStreamRepository`), which is the YouTube Music client.

Resilience already built in, before adding more:

- **Last-successful-client fast path** (`lastSuccessfulClientKey`) — the client that
  worked last is tried first.
- **Per-video, per-client backoff** — a client that fails with 403/404/410/416 is
  benched for 10 minutes (`FAILED_CLIENT_BACKOFF_MS`).
- **Bot-detection repair** — `repairAuthStateAfterBotDetection` invalidates tokens,
  refreshes `visitorData`/`dataSyncId`, remints and retries; there is also an
  IP-rotation refresh path.
- **Quality-ladder retry** — HIGHEST → HIGH → LOW.
- **Age-gate bypass clients** appended last, only when the content setting allows it.
- **Typed failures** — `LoginRequiredForPlaybackException`,
  `BotDetectionPlaybackException`, `BadStreamPlayerResponseException`.

## Audio format choice

`selectAudioFormatCandidates` filters to audio formats that have a usable URL or
cipher, then:

- `HIGHEST` (and `AUTO` off a metered network) sorts by **highest bitrate, uncapped**.
- `HIGH`/`LOW` prefer the best format *at or under* the target (160k/70k), falling
  back to the cheapest one above it.
- Ties break on codec rank, then sample rate.

**On "high-res" / itag 774:** there is nothing to add. Playback already asks the
YouTube Music client and already takes the highest bitrate on offer, so 774 is
picked whenever it is in the response. It usually is not — that format is tied to
a Premium entitlement, not to anything the client does. The itag and bitrate that
actually played are shown in the media-info sheet (`ui/utils/ShowMediaInfo.kt`), so
check there before assuming the picker is at fault.

Browser-side tricks that harvest 774 out of an authenticated web session do not
port: they work by letting Google's own player JS run in a real DOM with real
cookies, which a native client has no equivalent of.

## PO tokens

`ui/screens/settings/PoTokenScreen.kt` and `PoTokenExtractionActivity.kt` own the
UI; minting is BotGuard/QuickJS inside the core. Tokens are video-scoped and feed
the client selection above. `AutoChoosePlaybackClientKey` gates automatic client
choice — manual selection must stay available.
