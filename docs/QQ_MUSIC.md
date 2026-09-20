# QQ Music — playing from your own account

This source plays QQ Music through the account the user signs in with. It is the same client
protocol QQ Music's own desktop and web clients speak, and it is **not** an official integration:
Tencent's OpenAPI and its Android SDK are for enterprise partners (`暂不支持个人开发者申请接入`,
per <https://developer.y.qq.com/docs/openapi>), so there is no sanctioned path for an individual
account. Every open-source QQ Music client therefore implements the same client protocol, and so
does this one. It does so only for the account that signed in, at tiers that account holds, and
against no other account and no other party's content.

## Signing in

Settings → Integration → QQ Music shows a QR code. Scanning it with the QQ app authorises the
account; the app then holds the ticket Tencent issued for it. The chain is Tencent's own:

1. `ssl.ptlogin2.qq.com/ptqrshow` issues the image and a `qrsig` session cookie.
2. `ssl.ptlogin2.qq.com/ptqrlogin` is polled until the scan is confirmed; its `ptuiCB(...)` reply
   carries the state and a signed `uin`/`ptsigx` pair.
3. `ssl.ptlogin2.graph.qq.com/check_sig` exchanges that pair for the `p_skey` cookie.
4. `graph.qq.com/oauth2.0/authorize` turns `p_skey` into a one-shot authorisation code, which
   `QQConnectLogin.LoginServer` on `u.y.qq.com/cgi-bin/musicu.fcg` trades for the account ticket.

The ticket is stored in the app's own preferences and never leaves the device except as the
account's own credential on the account's own API calls. **Sign out** clears it. A ticket Tencent
has stopped honouring shows up as an unhealthy source check and as a fall-through on playback; there
is no silent use of a stale one.

## What it asks for, and what it plays

The url-minter is addressed by *resource name*, not by track: `<prefix><resource id><extension>`.
The prefix is the quality tier, so the app asks for exactly the tier the user picked:

| Setting | Plain resource | Encrypted resource |
| --- | --- | --- |
| Lossless (FLAC) | `F000….flac` | `F0M0….mflac` |
| High (320 kbps) | `M800….mp3` | `O8M0….mgg` |
| Standard (128 kbps) | `M500….mp3` | `O4M0….mgg` |

The plain resource is requested first, because a stream the account is served unencrypted needs no
local work at all. Only if the service returns no path for it does the app ask for the encrypted
resource, with `music.vkey.GetEVkey` and `songtype: 1` — the combination that makes the service
disclose an `ekey`. A tier the account does not hold comes back with an empty path, and playback
then falls through to the next source in the chain, exactly as an unentitled tier does for any other
source here.

## The containers it can read

A lossless tier sometimes arrives as a protected container rather than a plain file. Two families
exist and they are told apart by where the key lives:

- **QMC1** (`qmc0`, `qmc3`, `qmcflac`, `qmcogg`, …) is self-contained: a fixed keystream indexed by
  byte offset, so the whole file is XORed with it.
- **QMC2** (`mflac`, `mgg`, `mgg1`, …) is keyed per file. Files whose key is embedded in a footer —
  behind the `QTag` marker or behind a trailing little-endian length — are read: the footer key is
  base64, may be wrapped in Tencent's modified TEA (including the `EncV2` double wrap), and keys
  longer than 300 bytes select the modified RC4 instead of the map cipher.

The decrypted bytes must begin with a real container signature (`fLaC`, `OggS`, `RIFF`, `M4A`, a
chained MPEG frame) or the file is refused. A wrong key produces noise, not a plausible file, so
nothing unverified is handed to the decoder.

**What it cannot read:** files whose key is not in the file at all. Modern PC clients and Android
downloads keep the key in their own database instead, so those files do not decrypt and the track is
reported unavailable rather than guessed at. Because the plain resource is requested first, this
affects only the tracks the service will serve no other way.

## What it will not do

- **No entitlement is unlocked.** Only the tiers the account holds are requested; the others come
  back empty and playback moves on.
- **No advert, limit or purchase prompt is evaded.** Nothing here touches them.
- **Nothing is redistributed.** A protected container is decrypted on device, into the app's own
  cache, for local playback. It is never uploaded, re-shared, or written back to the service.
- **No account but the signed-in one is used.** There is no pool, no borrowed credential, and no
  fallback identity.

## Protocol references

The behaviour above follows these open-source clients, which are the only implementations of this
protocol:

| Reference | Used for |
| --- | --- |
| [L-1124/QQMusicApi](https://github.com/L-1124/QQMusicApi) | the QR chain, `musicu.fcg` envelopes, the `comm` profiles, the filename rule, the quality tables |
| [jsososo/QQMusicApi](https://github.com/jsososo/QQMusicApi) | the legacy `vkey.GetVkeyServer` param shape and the `sip` selection |
| [listen1/listen1_chrome_extension](https://github.com/listen1/listen1_chrome_extension) | the search request and the `req.data.body.song.list` response |
| [lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) | the song-detail call and `track_info` response |
| [unlock-music](https://github.com/unlock-music) (DMCA'd; read via a mirror) and its Go/C++ ports | the static keystream, the map and RC4 ciphers, the TEA key wrapping, the footer grammar |
| [mzj3920/qqmusic-decrypt](https://github.com/mzj3920/qqmusic-decrypt) | the fixed 128-byte key, the `STag`/`musicex` footers and the published key fixtures |

The tests under `app/src/test/.../qqmusic` carry the same references' known-answer vectors, so a
change to any of the cipher arithmetic fails against their numbers rather than this app's own.

## Known gaps

- **`oflac` is not implemented as a format.** No source in the reference set names it as a QMC
  container, and the reference set is explicit that the `ofl*` extension it does know (`ofl_en`) is
  a different scheme belonging to a different service. A file served under it is therefore treated
  as a container without a footer key and will fail the signature check.
- **`RS01` is not used.** One client lists it as a hi-res FLAC prefix; it is absent from the
  maintained enum, so this source asks for the tiers it can corroborate.
- **Keys stored off the file are unsupported**, for the reason above.
- **`musics.fcg` request signing is not implemented** because it is not needed: the `sign=`
  parameter belongs to that endpoint, while `musicu.fcg` authenticates with the account ticket alone.
- **The CDN list is served over cleartext HTTP.** This app's network security config refuses
  cleartext, so requests go to Tencent's own `https://isure.stream.qqmusic.qq.com/` host — the same
  fallback the reference clients use when the list is empty. The path's `vkey` is what authorises the
  fetch, so which of Tencent's hosts serves it does not change what is served.
