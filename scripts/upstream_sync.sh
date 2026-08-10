#!/usr/bin/env bash
# upstream_sync.sh — keep this fork's dev in sync with rukamori/ArchiveTune dev.
#
# What it does, in order:
#   1. Preflight checks (clean tree, secrets present)
#   2. Push a backup branch of dev (undo button)
#   3. Sync the vossgraves/core submodule fork with rukamori/core (AI if conflicts)
#   4. Merge upstream/dev into dev (AI resolves conflicts per the constitution)
#   5. Verify: no markers, protected files untouched, fork features intact
#   6. Build gate: assembleGmsMobileUniversalDebug must succeed
#   7. Deliver: clean merge -> push dev; AI-resolved -> push sync branch (PR opened by workflow)
#
# Required env: SYNC_PAT, GH_TOKEN
# Optional env: AI_API_KEY, AI_BASE_URL, AI_MODEL, MODELS_TOKEN
set -uo pipefail

log()  { echo "[sync] $*"; }
warn() { echo "::warning::$*"; }
die()  {
  echo "::error::$*"
  echo "result=failed" >> "$GITHUB_OUTPUT"
  exit 1
}

REPORT=/tmp/sync_report.md
AI_RESOLVED_FILES=()
PROTECTED_RESTORED=()
AI_USED=false

# --- Fork invariants (keep in sync with AGENTS.md) ---------------------------
NEVER_TOUCH=(
  "app/persistent-debug.keystore"
)
EXPECTED_APP_ID='applicationId = "moe.rukamori.archivetune"'
# -----------------------------------------------------------------------------

out() { echo "$1=$2" >> "$GITHUB_OUTPUT"; }

# =============================================================================
log "Phase 0: preflight"
# =============================================================================
[ -n "${SYNC_PAT:-}" ]  || die "SYNC_PAT is not set"
[ -n "${GH_TOKEN:-}" ]  || die "GH_TOKEN is not set"
[ -z "$(git status --porcelain)" ] || die "working tree is dirty"
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

git remote add upstream https://github.com/rukamori/ArchiveTune.git 2>/dev/null || true
git fetch upstream dev --quiet || die "could not fetch upstream/dev"

BEHIND=$(git rev-list --count HEAD..upstream/dev)
AHEAD=$(git rev-list --count upstream/dev..HEAD)
log "dev is $AHEAD ahead, $BEHIND behind upstream/dev"
out "behind" "$BEHIND"

if [ "$BEHIND" -eq 0 ]; then
  log "Already up to date. Nothing to do."
  out "result" "uptodate"
  exit 0
fi

# =============================================================================
log "Phase 1: backup"
# =============================================================================
BACKUP_BRANCH="backup/dev-auto-$(date -u +%Y%m%d-%H%M)"
git branch "$BACKUP_BRANCH" HEAD
git push --quiet origin "$BACKUP_BRANCH" || die "could not push backup branch"
log "Backup pushed: $BACKUP_BRANCH"
out "backup" "$BACKUP_BRANCH"

# =============================================================================
log "Phase 2: sync vossgraves/core with rukamori/core"
# =============================================================================
CORE_DIR=$(mktemp -d)
git clone --quiet "https://x-access-token:${SYNC_PAT}@github.com/vossgraves/core.git" "$CORE_DIR" \
  || die "could not clone vossgraves/core"
pushd "$CORE_DIR" >/dev/null
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git remote add upstream https://github.com/rukamori/core.git 2>/dev/null || true
git fetch upstream main --quiet
CORE_BEHIND=$(git rev-list --count origin/main..upstream/main)
log "vossgraves/core is $CORE_BEHIND behind rukamori/core"
if [ "$CORE_BEHIND" -gt 0 ]; then
  git merge --no-ff --no-commit upstream/main
  CORE_MERGE_RC=$?
  if [ "$CORE_MERGE_RC" -ne 0 ]; then
    warn "core merge has conflicts — invoking AI resolver"
    mapfile -t CORE_CONFLICTS < <(git diff --name-only --diff-filter=U)
    [ "${#CORE_CONFLICTS[@]}" -gt 0 ] || die "core merge failed but no conflicts found"
    python3 "$GITHUB_WORKSPACE/scripts/ai_resolve.py" "${CORE_CONFLICTS[@]}" \
      || die "AI could not resolve core conflicts"
    AI_USED=true
    git add -A
  else
    log "core merge clean"
  fi
  git commit --quiet -m "chore(sync): merge rukamori/core main ($CORE_BEHIND commits)" \
    || die "core merge commit failed"
  git push --quiet origin HEAD:main || die "could not push vossgraves/core"
  log "vossgraves/core updated"
fi
popd >/dev/null
CORE_NEW_SHA=$(git -C "$CORE_DIR" rev-parse HEAD)
log "core pinned at ${CORE_NEW_SHA:0:9}"

# =============================================================================
log "Phase 3: merge upstream/dev"
# =============================================================================
git merge --no-ff --no-commit upstream/dev
MERGE_RC=$?

if [ "$MERGE_RC" -ne 0 ]; then
  mapfile -t CONFLICTS < <(git diff --name-only --diff-filter=U)
  [ "${#CONFLICTS[@]}" -gt 0 ] || die "merge failed without file conflicts (e.g. untracked-file collision) — manual look needed; dev untouched (backup: $BACKUP_BRANCH)"
  log "Merge conflicts in ${#CONFLICTS[@]} file(s): ${CONFLICTS[*]}"

  AI_FILES=()
  SUBMODULE_PATHS=$(git config --file .gitmodules --get-regexp path 2>/dev/null | awk '{print $2}')
  for f in "${CONFLICTS[@]}"; do
    SKIP=false
    for p in "${NEVER_TOUCH[@]}"; do
      if [ "$f" = "$p" ]; then SKIP=true; break; fi
    done
    if $SKIP; then
      # Fork identity/config always wins; upstream changes here are dropped.
      git checkout --ours -- "$f" && git add "$f"
      PROTECTED_RESTORED+=("$f")
      warn "protected file conflict — kept fork version: $f"
    elif echo "$SUBMODULE_PATHS" | grep -qx "$f"; then
      # Submodule pointer conflicts are resolved deterministically by the
      # pinning step below — never send them to the AI.
      log "submodule pointer conflict: $f — will be pinned deterministically"
    else
      AI_FILES+=("$f")
    fi
  done

  if [ "${#AI_FILES[@]}" -gt 0 ]; then
    log "AI-resolving ${#AI_FILES[@]} file(s)"
    python3 scripts/ai_resolve.py "${AI_FILES[@]}" \
      || die "AI resolver failed — merge left unfinished on the runner (dev untouched, backup: $BACKUP_BRANCH)"
    AI_USED=true
    AI_RESOLVED_FILES+=("${AI_FILES[@]}")
    for f in "${AI_FILES[@]}"; do
      if grep -qE '^(<<<<<<<|=======|>>>>>>>)' "$f"; then
        die "conflict markers still present in $f after AI resolution"
      fi
      git add "$f"
    done
  fi
else
  log "Merge is clean (no conflicts)"
fi

# --- Submodule pointers -------------------------------------------------------
# rukamori-owned submodules: adopt upstream's recorded pointers exactly.
for sm in lyrics IconPack morideobfuscator; do
  SHA=$(git ls-tree upstream/dev "$sm" | awk '{print $3}')
  if [ -n "$SHA" ]; then
    (cd "$sm" && git fetch --quiet origin && git checkout --quiet "$SHA") \
      || die "could not pin submodule $sm to upstream pointer $SHA"
    git add "$sm"
    log "submodule $sm -> ${SHA:0:9} (upstream pointer)"
  fi
done
# core: pin to OUR fork's freshly merged commit (never rukamori's pointer).
(cd core && git fetch --quiet origin main && git checkout --quiet "$CORE_NEW_SHA") \
  || die "could not pin core submodule to $CORE_NEW_SHA"
git add core
log "submodule core -> ${CORE_NEW_SHA:0:9} (vossgraves/core)"

# =============================================================================
log "Phase 4: verification"
# =============================================================================
# 4a. applicationId must remain the fork's
grep -q "$EXPECTED_APP_ID" app/build.gradle.kts \
  || die "applicationId changed by merge — refusing to continue"

# 4b. fork features must still be present and wired
grep -q "resolveMultiSourceDataSpec" \
  app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt \
  || die "multi-source resolver no longer wired in MusicService.kt"
[ -d app/src/main/kotlin/moe/rukamori/archivetune/tidal ] \
  || die "tidal package missing after merge"
[ -d app/src/main/kotlin/moe/rukamori/archivetune/audiosource ] \
  || die "audiosource package missing after merge"
[ -d app/src/main/kotlin/moe/rukamori/archivetune/telegram ] \
  || die "telegram package missing after merge"
grep -q "TelegramDataSource" \
  app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt \
  || die "telegram data source no longer wired in MusicService.kt"
grep -q "persistent-debug.keystore" .github/workflows/build.yml \
  || die "fork CI signing patch (persistent-debug.keystore) lost in build.yml"

# 4b2. koiverse phone-home must never come back
if grep -rq "koiiverse.cloud" app/src canvas/src; then
  die "koiverse phone-home reintroduced after merge"
fi
if grep -rq "TogetherOnlineEndpoint" app/src; then
  die "koiverse together path reintroduced after merge"
fi
grep -q "TogetherPublicServers" \
  app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt \
  || die "vivimusic listen-together path lost after merge"

# 4c. commit the merge, then prove protected files are byte-identical to backup
git commit --quiet -m "chore(sync): merge upstream/dev ($BEHIND commits) + core submodule sync" \
  || die "merge commit failed"
for f in "${NEVER_TOUCH[@]}"; do
  if ! git diff --quiet "$BACKUP_BRANCH" HEAD -- "$f" 2>/dev/null; then
    die "protected file changed by merge: $f (restoring is not enough — aborting; backup: $BACKUP_BRANCH)"
  fi
done
log "verification passed"

# =============================================================================
log "Phase 5: build gate (fork contract tests + assembleGmsMobileUniversalDebug)"
# =============================================================================
chmod +x gradlew
# 5a. Fork contract tests FIRST (palette, crossfade, artwork, multi-source,
#     presence) — they fail in minutes and prove no fork feature was broken.
./gradlew --console=plain :app:testGmsMobileUniversalDebugUnitTest \
  || die "fork contract tests failed — the merge broke a fork feature; nothing pushed to dev (backup: $BACKUP_BRANCH)"
log "contract tests OK"
# 5b. Full APK build.
./gradlew --console=plain assembleGmsMobileUniversalDebug --warning-mode summary \
  || die "build failed — nothing pushed to dev (backup: $BACKUP_BRANCH)"
log "build OK"

# =============================================================================
log "Phase 6: deliver"
# =============================================================================
SYNC_BRANCH=""
if $AI_USED; then
  SYNC_BRANCH="sync/auto-$(date -u +%Y%m%d-%H%M)"
  git push --quiet origin "HEAD:$SYNC_BRANCH" || die "could not push $SYNC_BRANCH"
  out "result" "ai"
  out "branch" "$SYNC_BRANCH"
  log "AI-resolved merge pushed to $SYNC_BRANCH (PR will self-merge when CI passes)"
else
  git push --quiet origin HEAD:dev || die "could not push to dev"
  out "result" "clean"
  log "clean merge pushed straight to dev"
fi

# =============================================================================
log "Phase 7: report"
# =============================================================================
{
  echo "## Upstream sync report ($(date -u +%Y-%m-%dT%H:%MZ))"
  echo
  echo "- **Result:** $($AI_USED && echo 'AI-resolved, delivered as self-merging PR' || echo 'clean, pushed to dev')"
  echo "- **Pulled in:** $BEHIND commit(s) from [rukamori/ArchiveTune@dev](https://github.com/rukamori/ArchiveTune/tree/dev)"
  echo "- **Backup branch:** \`$BACKUP_BRANCH\`"
  [ -n "$SYNC_BRANCH" ] && echo "- **Sync branch:** \`$SYNC_BRANCH\`"
  echo "- **Run:** $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  echo
  echo "### Build gate"
  echo "\`assembleGmsMobileUniversalDebug\` passed on the runner before anything was pushed."
  echo
  echo "### Submodule actions"
  echo "- \`core\` -> vossgraves/core @ \`${CORE_NEW_SHA:0:9}\` (merged with rukamori/core first)"
  echo "- \`lyrics\`, \`IconPack\`, \`morideobfuscator\` -> upstream pointers"
  if [ "${#AI_RESOLVED_FILES[@]}" -gt 0 ] || [ "$AI_USED" = true ]; then
    echo
    echo "### AI-resolved files (review these)"
    for f in "${AI_RESOLVED_FILES[@]}"; do echo "- \`$f\`"; done
    if $AI_USED && [ "${#AI_RESOLVED_FILES[@]}" -eq 0 ]; then
      echo "- (conflicts were in the \`core\` submodule — see vossgraves/core history)"
    fi
  fi
  if [ "${#PROTECTED_RESTORED[@]}" -gt 0 ]; then
    echo
    echo "### Protected files kept at fork version (upstream change dropped — review if wanted)"
    for f in "${PROTECTED_RESTORED[@]}"; do echo "- \`$f\`"; done
  fi
  echo
  echo "### New upstream commits"
  echo '```'
  git log --oneline "$BACKUP_BRANCH"..upstream/dev | head -50
  echo '```'
  echo
  echo "### Rollback"
  echo '```bash'
  echo "git checkout dev && git reset --hard $BACKUP_BRANCH && git push --force-with-lease"
  echo '```'
} > "$REPORT"

log "done ($($AI_USED && echo ai || echo clean))"
exit 0
