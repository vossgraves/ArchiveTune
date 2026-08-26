/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Apple Music sign-in state. Login-only by design: no pool participation, and
 * independent of the Developer Options manual-login settings.
 *
 * Full-track streaming through Apple's API requires BOTH a developer token
 * (ES256 JWT from an Apple Developer Program key) and a per-user Music User
 * Token plus an active Apple Music subscription. Both are stored here when the
 * user signs in; the playback resolver can only engage once both exist.
 */
val AppleMusicMediaUserTokenKey = stringPreferencesKey("appleMusicMediaUserToken")
val AppleMusicDevTokenKey = stringPreferencesKey("appleMusicDevToken")
val AppleMusicAccountNameKey = stringPreferencesKey("appleMusicAccountName")
