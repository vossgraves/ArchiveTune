# ArchiveTune handoff

## Where things are

`canary` is the integration branch (PRs #162, #163, #164 are merged into it). It has
not been merged into `dev` — the device QA in [RELEASES.md](RELEASES.md) is still
outstanding, and that is the only thing gating the merge.

Shipped on `canary` and not yet device-tested:

- Yuma glass visual system on the shared settings row, so all 31 settings screens
  restyle at once. ArchiveTune routes, search anchors and provider pages are intact.
- Crossfade recovery when the outgoing player hits `STATE_ENDED` mid-fade
  (`CrossfadePolicy.shouldResumeAfterEnded`, unit tested). This is the fix for the
  reported "second song stops" bug and is the single highest-value thing to verify.
- Shorter buffers on low-RAM devices and in battery saver
  (`MusicService.useConservativePlaybackProfile`). Sampled once per player build.
- Remote stats: Stats screen can read YouTube and Spotify history, not just local.
- AI plumbing: one `Preferences.toAiServiceConfig()` instead of five copies, DeepL and
  Mistral wired into translate/romanize, OpenRouter base-URL normalisation, and the
  Gemini key moved out of the query string into the `x-goog-api-key` header.
- Opt-in read-only Spotify Recently Played in History.

## Known gaps on canary

- `StatsScreen.kt` has hardcoded English UI strings ("Top tracks", "Listening by hour",
  "Unknown artist", the source and range labels). They need extracting to
  `archivetune_strings.xml` before this reaches a release channel.
- `AppleMusicLoginScreen` no longer detects a completed sign-in — the cookie probe and
  `setAcceptThirdPartyCookies` were removed. The user must back out of the WebView
  manually. Decide whether to restore the probe or keep the manual flow.
- `QobuzLoginScreen.saveToken` drops invalid credentials with no toast, so a failed
  login looks like nothing happened.

## Research conclusions

SpatialFlow uses a second Media3 player, 50–60s buffering, a 250ms progress loop and a
simpler linear crossfade handoff. ArchiveTune already has stronger readiness, generation,
cache, codec, audio-route and promotion safeguards — do not copy it wholesale; measure first.

The `4nx3b/ArchiveTune` `dev` snapshot compared against was `0a6062248`. Its crossfade
pause fix is already represented here. The baseline profile and further preload/lifecycle
fixes are worth porting individually; wholesale sync is prohibited by the fork invariants.

SpotifySync drives an invisible Spotify WebView at low volume to mirror playback into
Spotify. That is not a passive history API and carries battery, privacy and account risk.
The shipped option is read-only Recently Played visibility and starts no playback.
