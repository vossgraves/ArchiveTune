# Source sign-in screens

Four sources sign in through a WebView because none of them offers a usable device flow:
Tidal, Qobuz, Deezer and Apple Music. Each screen's job is the same — get a credential the
provider will accept, prove it before saving, and never look idle.

| Source | Credential | How it is captured | Proof before saving |
|---|---|---|---|
| Tidal | PKCE refresh + access token; web-player Bearer as fallback | redirect interception, then a `fetch`/XHR header hook | `exchangePkceCode` / `buildSessionFromBearer` |
| Qobuz | user auth token + app id + app secret | header hook, plus a bundle scrape for the secret | `QobuzAudioProvider.verifyToken` |
| Deezer | `arl` cookie | `CookieManager` (the cookie is HttpOnly) | `DeezerAudioProvider.verifyArl` |
| Apple Music | Music User Token (+ developer token) | MusicKit instance poll, localStorage fallback | none available — the token shape is the only check |

## Why nothing saves unverified

Three of the four hand out a credential that *looks* right before anyone has signed in:

- Deezer issues an `arl` to anonymous visitors.
- Tidal's web player sends a Bearer on first load, before the sign-in form is submitted.
- Qobuz's bundle contains dozens of 32-character hex strings, and the app secret is one of them.

So presence is not proof, and a screen that saves on presence produces an account that looks
connected and fails on the first play. Each screen calls the provider's own health check and
only writes to DataStore once it comes back positive.

## The Qobuz app secret

This one cannot be solved by matching. The secret is a bare 32-character lowercase hex string
with no reliable marker around it, and webpack chunk hashes and asset digests are the same shape.
An earlier version took the first hex string it found, excluded the md5 of the empty string, and
reported success — which is how a saved Qobuz session could fail every stream request with
`invalid request signature`.

`QobuzBundleSecrets` therefore collects *candidates* and orders them, and the login screen tries
each against `verifyToken` until one signs a stream request. The ordering only decides who pays
the network cost first:

1. `keyed:` — found next to the app id, which the current bundle generation does. Usually one, and
   usually correct.
2. `legacy:` — an older bundle splits the secret across a `seed` and a timezone-keyed
   `info`/`extras` pair which concatenate into base64 with 44 trailing characters of filler.
   The hook reports the fragments and `decodeLegacy` reassembles them, so the arithmetic is unit
   tested rather than buried in injected JavaScript.
3. `hex:` — everything else, capped at `DEFAULT_LIMIT` so a bundle full of hashes cannot turn one
   sign-in into a hundred requests.

A rejected candidate is remembered, so a second batch of scripts never pays for it again. When the
whole pool is rejected the screen says so and offers the manual paste field, which goes through
the same verification.

## Never going quiet

Every failure path reports. That is the fix for "sometimes the WebView just doesn't work": the
credential search is asynchronous and has no natural end, so a screen that only speaks on success
is indistinguishable from a screen that is still trying. Deezer says when the cookie belongs to an
anonymous visitor, Qobuz says when no candidate signed, Apple says when its poll gave up after two
minutes. Tidal's rejected first Bearer is the one deliberate silence — it happens on every load
before sign-in, so a toast there would fire on the normal path.

## The QQ Music QR sign-in

QQ Music has no personal-developer API, so the source signs in the way the official client does:
the settings card requests a QR code, the user scans it with the QQ Music app, and the app polls the
login ticket until it is confirmed. The chain is `ptqrshow` → `ptqrlogin` → `check_sig` → the
`oauth2.0/authorize` hop → `QQConnectLogin/QQLogin`, and what comes back is the ticket/cookie set the
catalogue and the stream minter need.

Two things follow from that shape, and both are deliberate:

- **The QR expires, and the card says so.** The poll has four distinguishable states — waiting for a
  scan, scanned and waiting for confirmation, expired, refused — and each is rendered, with a refresh
  that re-requests the code. This is the same rule as the rest of this file: a screen that only
  speaks on success is indistinguishable from one that is still trying.
- **Signing out is a first-class action.** The stored ticket is not a session the app can refresh on
  its own, so the signed-in card carries the sign-out, and a failed sign-out is reported rather than
  leaving a stale card.

The account is the user's own; nothing is pooled, shared, or minted on anyone's behalf, and the
service's own quality locks are respected rather than worked around.
