# Amazon Music — how playback works and what it needs

Amazon playback resolves through a self-hosted **Amazon Music Stream API** instance that the user
adds in Integration → Amazon Music. No instance is shipped: the list starts empty, exactly like the
Tidal instance list, because the ecosystem has no working public default. An unconfigured source
resolves nothing and playback falls through to the next source.

## How a track plays

1. `AmazonAudioProvider.resolveByMetadata` asks the instance for the track by metadata
   (title / artist / album / duration). Two route generations are supported because hosts vary:
   - gen-2 `GET {base}/api/v2/track/?…&intent=stream&quality=…` (the `playback[]` envelope), and
   - gen-1.5 `GET {base}/api/track/?track=&artist=&…` (the `stream_url` shape),
   with a `404` on gen-2 falling back to gen-1.5.
2. The response carries a stream URL plus a raw CENC content key. The provider downloads the
   bytes and decrypts them on-device with `AmazonCencDecryptor` (the web player offloads this to
   the browser's ClearKey/EME stack; we have no EME, so we do it ourselves), then hands the
   playback layer a local file through the ordinary `DirectStream` path.
3. The result is matched against the query by the same `TitleMatch` gate every other source uses,
   and falls through to the next source on a miss.

## Authorization

Instances are commonly gated behind Cloudflare Turnstile. Two ways to satisfy them, both in the
Amazon settings screen:

- **Authorize with Turnstile** — opens `AmazonTurnstileActivity`, which renders the widget on the
  web player's own origin in a WebView, exchanges the solved token for the instance's JWT, and
  stores the JWT with its expiry (~1 h). Re-run it when the status row says it expired.
- **Bypass token** — the instance operator's `bypass_token`, sent as a query parameter.

Without either, the resolver declines every track (that is the "Never authorized" state in
settings).

## Adding an instance

Instance URLs are entered one per line in the settings screen and tried in order, first success
wins. Any deployment of the Amazon Music Stream API shape works; the app probes `/health` for the
source-check row so a dead host is reported as such rather than silently failing.

## Instances from the pool

The ArchivePool serves `amazon-music` hosts as well, so the source works without the user typing a
URL: the accounts feed carries each pooled instance's `baseUrl` in plain text and its material
(`bypassToken`, `turnstileJwt`) encrypted, with the JWT's expiry also plain text so a stale token can
be skipped rather than spent on a call the instance must reject.

`AmazonInstances.merge` is what the resolver and the settings screen both read:

- the user's own entries first, then the pooled ones, de-duplicated by normalised URL;
- material never crosses hosts — a bypass token belongs to the operator who issued it and a solved
  Turnstile JWT to the origin that handed out the challenge, so the user's token is not offered to a
  pooled host (nor the pool's to the user's);
- a pooled host that arrives with no usable material is skipped, and one whose only JWT has expired
  is skipped until the pool refreshes it.

The settings screen shows how many pooled instances stand behind the user's own, and the source
check probes the same assembled list, so "Amazon is ready" describes what playback will actually do.
A pool deployment that has collected no Amazon host yet is the normal state for a fresh deployment —
the user's own instance is then the only way in.
