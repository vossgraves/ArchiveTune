# ArchiveTune handoff

## Current delivery

- Active branch: `hoplite/kalchedon-2e14097c-yuma-settings-ui`
- Target: `canary`
- Pull request: [#163](https://github.com/vossgraves/ArchiveTune/pull/163)
- Latest published head: `1beb8197c07c1d7d483b459f09061f9f1f910a2b`
- Canary release assembly fix: PR #162, merged.

PR #163 contains the Yuma-style settings visual system while retaining ArchiveTune routes, search anchors, provider pages, and fork-specific settings. The branch now also contains explicit recovery when a crossfade reaches `STATE_ENDED` without completing its handoff, plus an opt-in Spotify recently-played history setting.

## Verification state

- `git diff --check`: required before publication.
- Focused crossfade tests cover gain, readiness, advancement, promotion, and ended-transition recovery.
- The supplied playback log showed Tidal without a configured instance and Apple Music without a usable Widevine key before YouTube recovered the track. It did not prove a SpatialFlow crossfade fault.
- Canary CI is required for compiler and release verification. Physical-device QA remains outstanding.

## Research conclusions

SpatialFlow uses a second Media3 player, 50–60 second buffering, a 250 ms progress loop, and simpler linear crossfade handoff. ArchiveTune already has stronger readiness, generation, cache, codec, audio-route, and promotion safeguards. Do not wholesale copy SpatialFlow; measure CPU, network, codec, cache, and thermal behavior first.

The upstream `4nx3b/ArchiveTune` `dev` snapshot used for comparison was `0a6062248253df7802c1575e55a8b2005c6243c5`. Its crossfade pause fix is already represented in this fork. Focused upstream candidates include the baseline profile and further preload/lifecycle fixes; wholesale synchronization is prohibited by the fork invariants.

SpotifySync drives an invisible Spotify WebView and low-volume playback to mirror current media into Spotify. That is not a passive history API and would add battery, privacy, and account risk. Stash uses broader account/library synchronization. ArchiveTune’s new option is read-only Spotify Recently Played visibility in History and does not start background playback.

## Next action

Run focused tests and inspect Canary CI. Then perform device QA with crossfade on and off, source fallback enabled, Spotify history enabled, and low-end/battery-saver conditions before merging to `dev`.
