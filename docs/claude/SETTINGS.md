# Settings route and surface inventory

## Stable navigation

Settings search uses parent routes and `?scrollTo=` anchors. Preserve both when changing groups. Provider-specific pages remain reachable from Integration even when manual login is disabled if an account credential already exists.

Important Integration anchors include `external_sources`, `spotify`, `music_sources`, `lastfm_scrobbling`, `lastfm_account`, `listenbrainz`, and `cross_service_import`.

## Spotify integration

The Spotify group contains account login/logout, playlist visibility, playlist reload, and the opt-in `Sync Spotify listening history` switch. The switch exposes Spotify Recently Played in the History screen; it is intentionally passive and does not emulate Spotify playback.

## Apple Music experience status

The current Apple Music player, queue/lyrics presentation, animated artwork, playlist hero, sleep timer, sliders, and menu-header treatment are implemented. The current experience flag still couples the player style to the broader Apple presentation.

The following independent controls are not complete and must not be described as shipped: playlist UI, player menu, lyrics menu, Home style, Library style, Apple navigation pill, and a non-locking preset that writes those controls independently. YouTube/Spotify Apple-style Home variants and Spotify Recommended for Today are also open gaps. Direct authenticated Apple playback must remain separate from any future public catalog search/fallback work.

## Settings change checklist

- Preserve existing parent routes and search anchors.
- Add new preference keys with explicit defaults and localized descriptions.
- Keep settings available through both the page and settings search when appropriate.
- Update route inventory and tests for any renamed or moved entry.
- Verify back navigation, deep links, dialogs, toggles, sliders, and provider-specific pages on Canary.
