# ArchiveTune handoff

## Where things are

`canary` is the integration branch. `dev` follows it only after the device QA in
[RELEASES.md](RELEASES.md), which is the one thing that gates the merge — nothing else.

The canary release channel is live (`C…` tags); the in-app updater takes the newest, so a red
canary run that still publishes is the build users get. That is why the unit tests have their own
workflow: the APK jobs assemble releases and never compile `app/src/test`.

## Shipped on canary and not yet device-tested

- **Apple Music experience.** The library screen exists now (large title, chevron rows, the four
  sections opened in place), the tab bar is the reference's floating rounded bar with a solid accent
  pill behind the active tab, and the library root's spacing was tightened. See
  [SETTINGS.md](SETTINGS.md) for what is shipped versus still open.
- **Home pages are a set.** `ActiveHomeSourcesKey` holds the pages the user made available and the
  Appearance picker edits it in a multi-select sheet; the top bar shows a plain toggle at two pages
  and a switcher sheet beyond that. YouTube and Spotify today; the enum takes more.
- **Listen Together**, ported from `4nx3b/dev`: the room code now reaches the Metrolist server (so
  The Meowery works), guest song changes reach everyone, custom profile pictures, the room queue and
  suggest UI, chat notifications with a shade reply, R8 keep rules for the protobuf package, and the
  codec now decodes every payload it encodes — `decodeProtobufPayload` had arms only for
  server→client messages, so create-room, playback-action, suggest-track and buffer-ready all decoded
  as `null`. It also reads *presence*, not value, for optional fields: a room volume of 0 is a mute,
  and a track position of 0 is the start.
- **Playlist changes that stood alone**: CSV export, the create-playlist dialog flicker, the
  online-playlist list churn, and the export picker's icon. The playlist *screen* rework was not
  taken — see [fork-divergence.md](../fork-divergence.md) for that decision and its cost.
- **Amazon** plays through instances: the user's own first, then any the pool serves, each host
  carrying only its own material. The pool's instance tier is deployed.
- **QQ Music** plays from the user's own account (QR sign-in, catalogue, QMC decryption).
- **Splash**: the YumaPlayer opening animation, bounded so it cannot hold the launch (5 s ceiling,
  tap-to-skip, one dismissal path).
- **The crash** that killed the service at creation: `MusicService.sponsorBlockPlaybackController`
  had lost its `@Inject` annotation, so the `lateinit` was never assigned.

## Highest-value device checks

1. Launch: the splash must dismiss on its own and never block the content underneath.
2. The Apple Music library tab with the experience on, and the tab bar's shape/pill against the
   reference screenshots.
3. A Listen Together room against a public server: create, join, guest song change, chat, mute, and a
   guest joining The Meowery specifically (that is the Metrolist path).
4. Amazon: with a pooled instance present (the pool carries none until one is submitted), and with a
   hand-added one.

## Known gaps

- **QQ's live protocol is unverified.** No QQ account was available while implementing it: the request
  shapes come from the reference clients, not from a live call. The catalogue search response path in
  particular is read defensively with a fallback tree walk.
- **The Amazon pool carries no instance yet**, so a user with no instance of their own gets nothing;
  the source check says so rather than spinning.
- **The Listen Together codec's client→server decode arms are only exercised by tests** — this app
  sends those frames and never reads them back. Keeping the tests as the contract is deliberate.
- **`docs/` describes mechanisms, not procedures**: anything procedural lives in
  [RELEASES.md](RELEASES.md).

## Research conclusions

SpatialFlow uses a second Media3 player, 50–60 s buffering, a 250 ms progress loop and a simpler
linear crossfade handoff. This fork already has stronger readiness, generation, cache, codec,
audio-route and promotion safeguards — do not copy it wholesale; measure first.

The `4nx3b/ArchiveTune` comparison is a moving target: their `dev` was 1310 commits past the shared
base when the Listen Together and playlist port landed, so those came across as a scoped port rather
than a merge, and their playlist-screen rework was declined because it replaces this fork's
`MediaDetailHero` on six screens and drags in the haze/glass subsystem with it. The `echo/` YouTube
extraction stack remains the largest genuine gap on either side.

SpotifySync drove an invisible Spotify WebView at low volume to mirror playback into Spotify. That is
not a passive history API and carries battery, privacy and account risk. The shipped option is
read-only Recently Played visibility and starts no playback.
