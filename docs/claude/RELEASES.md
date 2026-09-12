# Canary and Nightly release procedure

## Build

Use the repository workflow and its existing signing secrets. Never commit or restore a keystore.

```bash
./gradlew assembleGmsMobileUniversalDebug
./gradlew :app:testGmsMobileUniversalDebugUnitTest
```

For a Canary candidate, inspect the GitHub Actions run for the `canary` branch, confirm the artifact is produced from the intended head SHA, and install the matching APK on a test device. Nightly uses the equivalent release-shaped workflow and must preserve the historical signing identity.

## Device QA

1. Install over the previous build to verify signing and migration.
2. Start a queue with at least three tracks from YouTube fallback and an enabled external source.
3. Test crossfade disabled, enabled, paused during fade, manual next/previous during fade, repeat-one, queue replacement, sleep timer, Bluetooth route changes, and screen/background transitions.
4. Confirm the second and third tracks continue without pressing Play. Capture logcat around any `STATE_BUFFERING`, `STATE_ENDED`, `PlaybackException`, source switch, or codec recovery.
5. Test low-end or battery-saver conditions with canvas/artwork and lyrics both enabled and disabled. Compare heat, buffer stalls, and startup latency.
6. Open Integration, enable Spotify history sync, open History, refresh, search, sign out, and verify the source disappears without background playback.
7. Walk the settings root, every provider page, settings search anchors, dialogs, sliders, toggles, and back navigation.
8. Verify update installation from the prior Canary/Nightly artifact before considering `dev` integration.

Do not merge into `dev` until the playback and installation checks pass on a physical device.
