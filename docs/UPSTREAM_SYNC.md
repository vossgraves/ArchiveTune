# Upstream Sync

This fork keeps its `dev` branch automatically in sync with
[`rukamori/ArchiveTune` @ `dev`](https://github.com/rukamori/ArchiveTune/tree/dev)
via `.github/workflows/upstream-sync.yml`.

- **Runs:** hourly (`cron: 17 * * * *`, best-effort) + manually via
  **Actions → Upstream Sync → Run workflow**. A run with **no upstream
  changes is a cheap no-op** (~30s: checkout + fetch + compare) — it does
  NOT back up, merge, build, or open issues. Real work only happens when
  rukamori has pushed new commits.
- **Clean merge + green build** → pushed straight to `dev`.
- **Conflicts** → resolved by AI (`scripts/ai_resolve.py`), gated on a green
  debug-APK build, delivered as a **PR that merges itself** — the companion
  `upstream-sync-merge.yml` workflow merges it once the PR build CI passes
  (no "auto-merge" repo setting needed).
- **Every run** pushes a `backup/dev-auto-*` branch before touching anything
  and opens a GitHub issue with a full report (commits pulled, AI-resolved
  files, submodule actions, rollback command).
- **Any failure** → nothing is pushed to `dev`; an issue is opened with logs.

## What the AI is instructed to protect

1. Fork features always survive: Tidal (`tidal/` package), the multi-source
   audio framework (`audiosource/`, `resolveMultiSourceDataSpec` in
   `MusicService.kt`), and the fork's `persistent-debug.keystore` CI signing
   patch.
2. Integration seams are union-merged (both sides kept).
3. Never-touch files are always kept at the fork version, even if upstream
   changes them (the change is dropped and flagged in the report):
   `Koiverse.jks`, `Koiverse.jks.base64`, `ArchiveTuneKoiverseServer.txt`,
   `DataServer.txt`, plus the `applicationId` in `app/build.gradle.kts`.
4. A merge is only delivered if (a) the fork contract tests pass
   (`:app:testGmsMobileUniversalDebugUnitTest` — palette, crossfade, artwork,
   multi-source, presence) and (b) `assembleGmsMobileUniversalDebug` builds.

## One-time setup

1. **PAT secret (`SYNC_PAT`).** Create a fine-grained personal access token
   (GitHub → Settings → Developer settings → Fine-grained tokens) with access
   to `vossgraves/ArchiveTune` **and** `vossgraves/core`, permissions:
   *Contents: write, Pull requests: write, Issues: write, Actions: write.*
   Add it as repo secret `SYNC_PAT`. (The built-in `GITHUB_TOKEN` cannot push
   to the `core` repo, cannot push workflow-file changes, and PRs created with
   it would not trigger CI — hence the PAT.)
2. **AI key (`AI_API_KEY`).** The OpenCode Go API key, used with
   `https://opencode.ai/zen/go/v1` (model `kimi-k3`). Add as repo secret
   `AI_API_KEY`. Optional: override endpoint/model with repo variables
   `AI_BASE_URL` / `AI_MODEL`. If the key is missing or the gateway fails,
   the workflow falls back to the free GitHub Models chain automatically.
3. **Issues enabled.** Forks often have Issues disabled — the workflow uses
   them for sync reports and failure alerts: repo → Settings → General →
   Features → ☑ **Issues**. Also enable Actions if the Actions tab shows the
   "scheduled workflows are disabled for forks" banner. (No auto-merge
   setting is needed — `upstream-sync-merge.yml` merges sync PRs after CI
   passes.)
4. This workflow file must exist on the **default branch** (`main`) for the
   schedule to fire, and the `scripts/` files must exist on `dev` (the
   workflow checks out `dev` and runs them from there).

## Rollback

Every run pushes `backup/dev-auto-<timestamp>` before merging. To undo a sync:

```bash
git checkout dev
git reset --hard backup/dev-auto-<timestamp>
git push --force-with-lease origin dev
```

## Troubleshooting

- **"SYNC_PAT secret is not set"** → do setup step 1.
- **Sync PR opened but never merges** → check the PR's Build Pull Request CI:
  if it failed, the merge is intentionally skipped (a comment explains why).
  If CI passed but the PR is still open, look at the latest *Upstream Sync
  Merge* run — or just merge the PR manually.
- **Sync issue says build failed** → read the linked run log; upstream likely
  needs a fix or the merge broke something. `dev` was not modified; the next
  hourly run will retry from the same state.
- **AI resolution failed** → the model chain was exhausted or a resolution was
  rejected (leftover markers). `dev` untouched. Re-run manually, or resolve
  locally: `git merge upstream/dev` in a clone and push a `sync/manual-*`
  branch + PR.
- **Rate limits (GitHub Models fallback)** → the free tier allows ~50
  high-tier requests/day; conflicts use a handful. Chronic failures mean you
  should set `AI_API_KEY` (step 2).

## Running it manually

**Actions → Upstream Sync → Run workflow** (branch `main`). Watch the log;
the report issue appears within a minute of the run finishing.

## 2026-08 koiverse cleanup (read before the next sync run)

The koiverse phone-home surface was removed from the fork:

- `TogetherOnlineApi` / `TogetherOnlineEndpoint` / `TogetherOnlineHost` and the
  `MusicTogetherConnectionMode.ONLINE` path are gone. Listen Together now runs
  on LAN (`TogetherServer`/`TogetherClient`) and the vivimusic public servers
  (`TogetherPublicServers`/`TogetherPublicClient`) only.
- `ArchiveTuneKoiverseServer.txt`, `DataServer.txt`, `Koiverse.jks`,
  `Koiverse.jks.base64` were deleted; `DataServer.txt` and the
  `DATA_SERVER_URL`/`API_BEARER_TOKEN`/`CANVAS_BEARER_TOKEN` BuildConfig fields
  are gone from `app/build.gradle.kts`.
- The canvas artwork proxy (`ArchiveTuneCanvas` → artwork-archivetune.koiiverse.cloud
  / artwork.boidu.dev) was removed; canvas now resolves via
  `SpotifyCanvasProvider` and `AppleMusicProvider`.
- The `moriextractor` submodule was dropped (phantom dependency) and the inert
  cipher UI (`ChiperSettings`, `MoriCipherUpdateWorker`, cipher package) was
  removed while the `morideobfuscator` module was kept.

If an upstream merge reintroduces any `koiiverse.cloud` reference or the
`TogetherOnlineEndpoint` path, `scripts/upstream_sync.sh` Phase 4 will abort.
Do not "restore" these files: they are fork-invariant removals.
