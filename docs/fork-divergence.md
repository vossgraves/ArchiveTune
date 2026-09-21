# vossgraves vs 4nx3b

Both forks descend from `646829cf` and have since taken the same features in different
directions, so a commit count says nothing useful — the trees are what matter. This compares
`vossgraves/canary` against `4nx3b/dev` at `28e0eb37b`, by file presence and then by what each
side's version of a shared subsystem actually does.

At that point 68 source files exist only there and 50 only here. The gap was 71 when this was first
written; the Apple Music catalog, SimpMusic's lyrics and queue sheets and the baseline profile have
come across since, and they have taken this fork's SponsorBlock and haptics in the other direction.
Regenerate the counts with `git ls-tree -r --name-only` on both trees and `comm` — the numbers age
faster than the prose.

Two file sets, deliberately left out of the tables below because they are noise: 33 drawables,
2 fonts and 3 Lottie JSONs that belong to the player skins and Lottie work named under
**Theirs, worth taking**.

## Theirs, worth taking

### The `echo/` YouTube extraction stack — the biggest gap

Nineteen files plus five assets, with no counterpart on our side:

| Area | Files |
|---|---|
| Resolution | `echo/EchoStreamResolver.kt`, `echo/PlaybackEngine.kt`, `echo/utils/Fix403.kt`, `echo/utils/PlaybackLogManager.kt` |
| Cipher | `CipherDeobfuscator`, `CipherWebView`, `FunctionNameExtractor`, `PlayerConfigParser`, `PlayerConfigStore`, `PlayerDatesStore`, `PlayerJsFetcher`, `RendererRecoveryPolicy` |
| PO token | `PoTokenGenerator`, `PoTokenWebView`, `JavaScriptUtil`, `PoTokenException`, `PoTokenResult` |
| SABR | `EjsNTransformSolver`, `SabrException` |
| Assets | `assets/solver/meriyah.js` (JS parser), `assets/solver/astring.js` (generator), `assets/solver/yt.solver.core.js`, `player_configs.json`, `player_dates.json` |

We do have a PO token path (`utils/potoken/BotGuardTokenGenerator`, `ChallengeParser`), but it is
a different and narrower approach: theirs parses the player JS with a real JS parser and
regenerates the n-transform, which is what survives a YouTube player change without a release.
This is the one item where their fork is ahead on capability rather than merely different.

### The SpatialFlow player skin

Seven files (`SpatialFlowPlayer`, `Components`, `Lyrics`, `QueueDrawer`, `SleepTimer`, `Typography`,
`WavySlider`) plus 17 drawables and a font. We have only the haptics, as
`playback/SpatialFlowHaptics.kt` — a PCM processor, not a skin. The whole player style is absent.
They have since taken that haptics processor from here, so the skin is the only part still split.

### Apple Music catalog — ported

`AppleMusicCatalog`, `AppleMusicModels`, `AppleMusicPlaybackResolver`, `AppleMusicSearchItem`,
`AppleMusicOnlineSearchResult` and `AppleMusicSearchViewModel` are here now, and `APPLE_MUSIC`
joins the `SearchProvider` enum, the source picker and the settings default. This fork already had
the other half — `AppleMusicAudioProvider`, `AppleMusicVirtualStream`, `AppleMusicPlayer`,
`AppleMusicQueueSheet`, `AppleMusicSlider`, `AppleMusicLyricsPolicy`, `AppleMusicExperience`,
`AppleMusicPlaylistHero` and a canvas provider — so this is the only fork with both.

### Robustness the other fork has and we do not

| File | What it does |
|---|---|
| `db/RecoveringOpenHelper.kt` (+ test) | Recovers from a corrupt Room database instead of crashing on open |
| `utils/StartupReadiness.kt` (+ test) | Gates work until startup has actually finished |
| `baseline-prof.txt` | Baseline profile — measurable cold-start win, costs nothing at runtime |
| `utils/NewReleaseCheckWorker.kt`, `NewReleaseNotificationManager.kt` | Notifies on new releases from followed artists |
| `tidal/TidalCanvasCheck.kt`, `CanvasCheckDialog.kt` | Checks whether a Tidal account can serve canvases |

### Their tests we lack

`LazyCacheTest`, `DownloadSnapshotStateTest`, `PlayerConnectionTest`, `BoundedSyncWorkersTest`,
`HomeRequestGateTest`, `HomeStateInputsTest`, plus the two named above.

### Cosmetic

`GlassScreenHeader`, `MuzoHomeSections`, `MuzoMenuComponents`, `LibraryChromeComponents`,
`SpotifyPlaylistMenu`, `BitChordMeshGradient`, `TikTokMeshBackdrop`, `SfProFontCatalog` +
`SfProFontPickerDialog`, `ui/lottie/ArchiveTuneLottie.kt` + 3 Lottie animations, and three player
widgets they factored out that this fork still inlines: `ToggleSegmentButton`,
`V9AnimatedPlaybackControls`, `WavySliderExpressive`.

The SimpMusic full-screen lyrics sheet, queue sheet and typography were on this list and are now
ported; SimpMusic's Show button opens its own lyrics rather than leaving for the shared screen.

## Telegram: the same engine, driven differently

Both forks ship TDLight 1.8.66 from the same `tdlight-2b51b33` release, both download
`libtdjni.so` at runtime with digest pinning, and both default `slimTdlib` to true. The
difference is the binding layer: they wrap it in **td-ktx** (`kotlinx/telegram/core/TelegramFlow`,
`ResultHandlerStateFlow`, `TelegramException`, plus `telegram/TdEngine.kt`), exposing TDLib as
coroutine flows. We drive `org.drinkless.tdlib.Client` directly from our own `TelegramClient`.

Theirs is the nicer API and cost them three files. Ours has no extra dependency and already
carries the three 1.8.66 schema fixes. Not a gap in either direction — a choice.

They also have `telegram/TgStrippedJpeg.kt`, which decodes Telegram's stripped-JPEG thumbnails.
That one is a real gap, and small.

## Ours, which they lack

- **Lyrics providers — eleven of them.** `DeezerLyricsProvider`, `TidalLyricsProvider`,
  `MegalobizLyricsProvider`, `SimpMusicLyricsProvider`, and six Paxsenix backends
  (Apple Music, Musixmatch, Netease, Spotify, YouTube, generic), plus
  `LyricsTranslationHelper` and `LyricsAnimationSettings`.
- **SponsorBlock.** `SponsorBlockModels`, `SponsorBlockRepository`,
  `SponsorBlockPlaybackController` — since taken by their fork, which added a `SponsorBlockUseCases`
  layer and a settings view model on top that this one does not have.
- **Video playback.** `VideoSurfaceManager`, `VideoSurfacePolicy`.
- **The Yuma design system.** `YumaTheme`, `YumaModifiers` — the glass the settings tree is
  built on.
- **Home and library.** `RukamoriHomeScreen`, `ExploreScreen`, `LibrarySourceSelector`,
  `LibrarySpotifySections`.
- **Together's public protocol.** `TogetherPublicProto` (+ codec test); theirs has the online
  transport only.
- **Haptics as a PCM processor.** `HapticsPcmProcessor` feeding `SpatialFlowHaptics` — also since
  taken by their fork.
- **Qobuz app-secret verification.** `QobuzBundleSecrets` (+ test) — see
  [source-logins.md](source-logins.md).
- **yt-dlp signature verification.** `assets/yt_dlp_public_key.asc`.
- Shared components: `MarqueeText`, `GridMenu`, `BigSeekBar`, `NavigationTile`, `TagChip`,
  `MenuHeaderCard`, `SpotifyPlayableRow`, `ReleaseNotesCard`, `UpdateInfoDialog`,
  `GridItemRandomizer`, `DebugPanel`, `PreBlurredArtwork`, `KeyUtils`, `NetworkUtils`.

## If only one thing gets ported

`echo/`. It is the only item on either list that changes what the app can still play after
YouTube ships a player change, and it is the only one that cannot be reconstructed from a
screenshot.

## Ported 2026-09-20 (Listen Together + playlist UI)

Taken from their `dev` onto `canary`, after the shared base had grown 1310 commits apart — so this
was a scoped port, not a merge:

- **Listen Together**: their room-code fix for the Metrolist server (`31ef54d64`), the protobuf half
  of `d0b8b86f2` (the `proguard-rules.pro` keep rules), `d184d6984` (guest song changes reaching
  everyone, custom profile pictures, room queue + suggest UI, the chat composer overlap) and the
  Listen Together and notification halves of `865e1de72`.
- **A real bug in our own codec**, found because their tests are the contract: our
  `decodeProtobufPayload` only had arms for server→client messages, so every payload the *client*
  sends — create-room, playback-action, suggest-track, buffer-ready — decoded as `null`. Twelve arms
  added; the four round-trip tests in `listentogether/ProtoWireTest.kt` are the regression net.
- **Playlist UI, the self-contained parts**: CSV export (`9b42c9176`) and the online-playlist
  list-state fix.

**Deliberately not taken**: their playlist-screen rework. On their side the six playlist screens
render `AppleMusicPlaylistHero` with a canvas slot and a `ScreenHeaderHaze`/glass subsystem (~41
files) *instead of* our `MediaDetailHero` (thumbnail, bookmark, download actions, its LiquidGlass
backdrop). Swapping it is a rewrite of the library's visual language rather than a port, and it
would take the Apple Music playlist header with it. If it is ever wanted, it is one decision —
adopt the hero *and* the haze subsystem together, on all six screens.

Also not taken: their `echo/` extraction stack (still the largest genuine gap, see above), and any
TikTok/lyrics/AOD work, which is theirs alone.

## Ported 2026-09-21 (their Listen Together chat suite, TikTok captions)

Second scoped port from their `dev` onto `canary`, again by patch rather than merge: the sha they
forked from and our rewrite-era history make a merge meaningless, and the shared base has grown
~1000 commits on each side since. Authorship is preserved on each commit, with
`(cherry picked from commit …)` trailers.

- **Listen Together — the chat suite** (`b5ee2b94a`, `4d5f880b1`, `408920ecd`): reactions, edits,
  deletes (local + host "delete for everyone"), pins with a carousel, typing indicators, the
  Instagram-style anchored action popup over the glass backdrop, the full Unicode 16 emoji keyboard
  (`EmojiCatalog.kt`), per-username persisted history with a `solo`-message wipe
  (`PersistedChatHistory` version 2), avatars before names, swipe-to-reply, and song sharing —
  `ListenTogetherSongPicker.kt` plus the `[LTS:…]` relay envelope and the in-room
  `playSharedTrack` path. Their build fixes came along: the emoji-grid key lambda (`dbd624f9b`).
- **Listen Together — scoping and protocol** (`1d4ddb74d`, `27fa4e7fb`): no chat UI at all on the
  protobuf server (the Meowery's Go server rejects unknown message types), scoped chat-history
  restore (nothing while alone, only with members actually present, ids remapped to the current
  session, `restored` flag + "older messages" divider) and guest song-change propagation (the
  player listener is attached on `JoinApproved`).
- **TikTok player — the karaoke caption strip** (`c0e18ca9d`, `d0b8b86f2`, `4d5f880b1`,
  `1d4ddb74d`): `TikTokMainLyrics.kt` renders only the active line under the artwork, and the page
  reserves a constant 168dp slot whenever the preference is on so the artwork never shifts. Their
  renderer is app-side on purpose: the library's `KaraokeLyricsView` paints a permanent 20dp/100dp
  vertical `DstIn` fade mask that dimmed wrapped second rows in a strip this short. Gated on
  `TikTokMainLyricsEnabledKey` (off by default), with a row in Appearance settings and an entry in
  the settings search index.
- **Canvas aspect fix** (`27fa4e7fb`): `CanvasArtworkPlayer` tracks `onVideoSizeChanged` and lays
  the frame at cover geometry inside a `clipToBounds` wrapper for the ZOOM path, so a stalled
  `videoSizeDp` can no longer leave a canvas stretched.

**Adapted, not copied:** their `LyricsEnhanced` carries a provider-header/composer-footer
plumbing (`lyricsProviderLabel`, `composerFooter`) from their own 2026-08 work that this tree never
had, and its romanisation rides inside the translation string (`compactTranslation`). Ours rides as
`phonetic` on karaoke syllables, so their compact-translation hunks do not apply and the strip uses
our renderer unchanged. Their `MessageCodec` is protobuf-javalite; ours is the hand-rolled
`ProtoWire`, and because chat travels only on the JSON-format servers (the protobuf server has no
chat relay) no codec arms were needed.

**Deliberately not taken:** the video half of `865e1de72`/`4d5f880b1` — the both-streams
`mainAudioReady` start barrier, `declareVideoFailure`, `MaxVideoRecoveryAttempts = 1` 1080p
recovery and the poisoned-cache eviction. This tree's `VideoArtworkPlayer` has since been rebuilt
(201 lines of divergence against their base, plus its own tiered sync/drift model in
`docs/video-sync.md`), so their patch is a rewrite of a working pipeline rather than a delta, and
it needs a device pass to validate. Also not taken, as before: `worklog.md` and `changelogs.md`,
which do not exist here.

**Gate residue removed** (not from them — our own leftovers): the `gatekeeper_connection_blocked`
string, whose only consumer was the deleted `GatekeeperViewModel`, and the four bearer/token
secrets the workflows still exported into the Gradle environment
(`API_BEARER_TOKEN`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER`) that no
build script or source file reads.
