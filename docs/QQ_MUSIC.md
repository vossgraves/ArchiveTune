# QQ Music — what has to be provisioned before it plays

The source is compiled in, off by default, and resolves nothing until a build carries Tencent Music
partner credentials. There is no path around that: QQ Music's catalogue and playback exist through
the TME partner programme (OpenAPI / QPlay), and there is no public personal-developer playback API.

## Applying

Register with Tencent's music developer platform — <https://developer.y.qq.com/> — and request
partner access (the contact published there is `qmopen@tencent.com`). A partnership issues an app id
and an app key, plus the endpoint documentation and signing scheme that go with them.

## What the maintainer sets

| Property | Purpose |
| --- | --- |
| `QQ_PARTNER_APP_ID` | The partner application id. |
| `QQ_PARTNER_APP_KEY` | The partner app key used to sign requests. Never commit it. |
| `QQ_PARTNER_API_BASE` | The API base from the partnership's own documentation. |

All three default to blank, and the provider refuses to resolve while any of them is unset.

**`QQ_PARTNER_API_BASE` has no default on purpose.** This repository cannot verify which host a
partnership uses, and naming one anyway would be inventing an integration. The value belongs in
`local.properties` or a CI secret, taken from the partnership's documentation.

## What the code does

- Catalogue search and playback against `{base}/search` and `{base}/playback`, signed with
  HMAC-SHA256 over `app_id`, `timestamp` and the request parameters in sorted order — the canonical
  layout the partnership documents. `QqMusicSignTest` pins that layout, because a signing mistake
  is the one failure here that still returns HTTP 200 and would otherwise look like an empty
  catalogue.
- The playback URL is used as-is over HTTPS: no custom scheme, no DRM session, no decryption step.

## What it will not do

- No `u.y.qq.com` / `musicu.fcg` calls, and no vkey construction from any leaked signing scheme.
- No decryption of `mflac`, `mgg` or any other encrypted container. A track the API offers only in
  one of those formats is reported unavailable and playback falls through. Expect a large part of
  the catalogue to behave that way even with valid credentials — that is the boundary-compliant
  outcome, not a bug to fix.
- No ad or limit circumvention: non-VIP qualities are simply not requested.

## Enabling it

The source is deliberately not in `AudioSourceConfig.DEFAULT_ORDER`, so the order picker never
offers it. Settings → Integration → QQ Music has the switch; turning it on writes QQ into the
stored order just above YouTube — the only position where a source acts as an override — which is
what makes the resolver consult it. Turning the switch off leaves the order alone and the resolver
skips disabled sources.