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
- **Home and library.** `RukamoriHomeScreen`, `ExploreScreen`, `LibrarySourcePills`,
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
