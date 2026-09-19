# Amazon Music — what has to be provisioned before it plays

The source is implemented and inert. It resolves nothing, and playback falls through to the next
source, until a build carries an approved Amazon Music Web API security profile. That is the
deliberate design, not an unfinished edge.

## Why it is inert

Amazon's Music Web API is approval-gated: playback sessions, catalogue search and the Widevine
licence that unlocks a track are all issued to an approved partner application, through the user's
own signed-in entitlement. There is no unapproved path that this repository will take — no instance
or stream proxy, no third-party key or licence service, no replaying another client's licence flow,
and no pooled *service* credential. Those are the boundaries the source was written under; see the
header of `app/src/main/kotlin/moe/rukamori/archivetune/amazon/AmazonMusicProvider.kt`.

## What the maintainer provisions

1. **An Amazon Music Web API agreement.** Amazon issues Web API access through their business
   development contact; the program requirements are published at
   <https://developer.amazon.com/docs/music/requ_AM-Program-Requirements.html>.
2. **A Login with Amazon security profile.** The client id from that profile is what
   `BuildConfig.AMAZON_LWA_CLIENT_ID` carries.
3. Set both values where the build reads them — a `local.properties` entry or a CI secret, never a
   committed file:

   | Property | Purpose |
   | --- | --- |
   | `AMAZON_LWA_CLIENT_ID` | The LWA security profile's client id. Blank in every build without one, which is what keeps the source inert. |
   | `AMAZON_API_BASE` | Web API base, defaults to `https://api.music.amazon.dev`. Only override if Amazon issues a different host. |

With the client id set, the source-check row in Settings reports credentials instead of "needs
approval", and the resolver starts trying Amazon before falling through.

## What the code does once it is configured

- Catalogue search against `{base}/v1/catalog/search`, and a playback session against
  `{base}/v1/playback/sessions` with `X-Amzn-Audio-DRMType: WIDEVINE` and the requested
  device capability (Ultra HD / HD / Standard, mapped honestly — asking for Ultra HD does not
  grant it).
- The manifest the session returns is played as DASH, and the licence request goes to the licence
  URL in that same response, signed with the signed-in user's own token. Amazon's server issues the
  keys for that user's entitlement.
- Accounts come from the personal sign-in first, then the pool. The pool list is read from memory
  once per playback attempt — never per song over the network.

## What it will not do

- Play a track the account is not entitled to, at a quality the account does not have.
- Skip, mute or filter anything the server injects.
- Use a pooled credential for the *service*, or share a user's session with anything but this app's
  own playback.
- Mute a `NO_MORE_SKIPS`-style restriction: the session's actions are honoured, not worked around.

## Response shapes

The request shape (endpoint paths, the `X-Amzn-Audio-*` headers, the session body) follows Amazon's
published documentation. The *response* schema is only documented behind the approval, so the
parsers read each value by its role across the shapes Amazon's samples agree on, and return null on
an unrecognised body. An unapproved or misconfigured build therefore falls through rather than
handing the player something it cannot play — and if Amazon's schema turns out to differ from what
is assumed here, the failure mode is a silent fall-through, which is why the parsers are permissive
rather than strict.