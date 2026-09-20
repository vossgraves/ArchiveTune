# Settings route and surface inventory

## Stable navigation

Settings search uses parent routes and `?scrollTo=` anchors. Preserve both when changing groups. Provider-specific pages remain reachable from Integration even when manual login is disabled if an account credential already exists.

Important Integration anchors include `external_sources`, `spotify`, `music_sources`, `lastfm_scrobbling`, `lastfm_account`, `listenbrainz`, and `cross_service_import`.

## Spotify integration

The Spotify group contains account login/logout, playlist visibility, playlist reload, and the opt-in `Sync Spotify listening history` switch. The switch exposes Spotify Recently Played in the History screen; it is intentionally passive and does not emulate Spotify playback.

## Apple Music experience status

Shipped and driven by the experience switch: the Apple Music player (with its queue sheet, inline
lyrics and mini header), the animated-artwork backdrop, the playlist hero, the sleep-timer sheet, the
sliders, and the menu-header treatment. The switch forces the player style and the tab bar, and it
keeps `LibraryStyleKey` in step — that coupling is deliberate: the switch is meant to turn the whole
experience on, and the library-style row is the way to turn on the library half alone.

Also shipped since this note was written:

- **Library screen** — with the library style on, the Library tab renders an Apple Music root (large
  title, chevron rows, hairline insets to the text column, the four sections opened in place under a
  back row) instead of the fork's chip row.
- **Tab bar** — the Apple Music style is a floating rounded bar, inset, with a solid accent pill
  behind the active tab, its glyph knocked out, and the label in the accent.
- **Home pages** — the Home tab's pages are a multi-select set (`ActiveHomeSourcesKey`) with a
  single-choice switcher sheet in the top bar when more than one is active; YouTube and Spotify
  today, and the enum/selector take more.
- **Player and lyrics overflow** — the player's more button opens the shared menus, which carry the
  Apple Music sleep-timer sheet and the lyrics menu.

Not complete, and must not be described as shipped: a non-locking preset that writes those
sub-controls independently, YouTube/Spotify Apple-style *Home variants* (the Home selector switches
sources, it does not restyle them), and Spotify's Recommended for Today. Direct authenticated Apple
playback must remain separate from any future public catalog search/fallback work.

## Settings change checklist

- Preserve existing parent routes and search anchors.
- Add new preference keys with explicit defaults and localized descriptions.
- Keep settings available through both the page and settings search when appropriate.
- Update route inventory and tests for any renamed or moved entry.
- Verify back navigation, deep links, dialogs, toggles, sliders, and provider-specific pages on Canary.
