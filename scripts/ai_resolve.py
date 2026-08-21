#!/usr/bin/env python3
"""ai_resolve.py — resolve git merge conflicts with an LLM.

Usage (from the repo root of the conflicted checkout):
    python3 ai_resolve.py <conflicted-file> [<conflicted-file> ...]

Providers (in priority order):
  1. Custom OpenAI-compatible endpoint (env AI_BASE_URL, AI_API_KEY, AI_MODEL)
     — e.g. OpenCode Go (https://opencode.ai/zen/go/v1) with kimi-k3.
  2. GitHub Models free tier (env MODELS_TOKEN) — fallback chain:
     openai/gpt-4.1 -> openai/gpt-4.1-mini -> openai/gpt-4o-mini

Exit code 0 = every conflict in every file resolved (no markers remain).
Exit code 1 = at least one file could not be fully resolved.
Stdlib only — no pip installs needed on the runner.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request

CONTEXT_LINES = 40
MAX_TOKENS = 4000
TIMEOUT = 180

CONSTITUTION = """You are resolving a git merge conflict in the ArchiveTune Android fork (vossgraves/ArchiveTune, branch `dev`) while merging in upstream (rukamori/ArchiveTune, branch `dev`).

Resolution rules, in priority order:
1. FORK FEATURES MUST SURVIVE. The fork adds: Tidal music source (package `tidal`, TidalAudioProvider, TidalInstanceHealthManager, TidalAccountManager, TidalSettings, TidalCookieUtils), a multi-source audio framework (package `audiosource`: Deezer/Amazon providers, IsrcResolver, AudioSourceConfig, and `resolveMultiSourceDataSpec` wired in MusicService.kt), and fork CI patches (persistent-debug.keystore signing fallback in workflow files). Never produce a resolution that deletes, disables, or unwires these.
2. UNION-MERGE integration seams. In shared files (MusicService.kt, PreferenceKeys.kt, NavigationBuilder, settings screens, build files), keep BOTH sides' changes: upstream's new code AND the fork's hooks. Do not pick one side wholesale unless the other side is clearly a strict subset.
3. Prefer upstream for everything else: upstream refactors, dependency bumps, UI changes unrelated to fork features.
4. THE RESULT MUST COMPILE. Only reference classes/symbols that are (a) imported in this file, (b) defined in this file, or (c) clearly part of the file's own framework. If one side references a symbol the other side deleted (e.g. upstream removed a class from another module), DO NOT reintroduce that reference — adapt the code to the removal instead. A syntactically valid merge that references a deleted class is a FAILURE.
5. The result must be syntactically valid for the file's language (Kotlin/Gradle/XML/YAML/Markdown): balanced braces/parens, consistent imports, no duplicate declarations, no leftover conflict artifacts.

Output contract (strict):
- Output ONLY the resolved code for this one conflict hunk — nothing else.
- No explanations, no commentary, no markdown code fences.
- Never emit <<<<<<<, =======, |||||||, or >>>>>>> markers."""


def build_providers():
    providers = []
    base = os.environ.get("AI_BASE_URL", "").rstrip("/")
    key = os.environ.get("AI_API_KEY", "")
    model = os.environ.get("AI_MODEL", "kimi-k3")
    if base and key:
        # Note: the OpenCode Go gateway rejects a `temperature` field for
        # kimi models — leave it unset there.
        providers.append({"base": base, "key": key, "models": [model],
                          "max_tokens": 8000, "temperature": None})
    token = os.environ.get("MODELS_TOKEN", "")
    if token:
        providers.append({
            "base": "https://models.github.ai/inference",
            "key": token,
            "models": ["openai/gpt-4.1", "openai/gpt-4.1-mini", "openai/gpt-4o-mini"],
            "max_tokens": MAX_TOKENS,
            "temperature": 0.1,
        })
    return providers


def call_llm(system, user):
    last_err = "no providers configured"
    for provider in build_providers():
        for model in provider["models"]:
            body = {
                "model": model,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
                "max_tokens": provider["max_tokens"],
            }
            if provider.get("temperature") is not None:
                body["temperature"] = provider["temperature"]
            payload = json.dumps(body).encode()
            req = urllib.request.Request(
                provider["base"] + "/chat/completions",
                data=payload,
                headers={
                    "Authorization": "Bearer " + provider["key"],
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    # Some gateways (OpenCode Go) reject default python UA.
                    "User-Agent": "opencode/1.0",
                },
            )
            try:
                with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                    data = json.loads(resp.read().decode())
                text = data["choices"][0]["message"]["content"]
                print(f"[ai] resolved via {model}", file=sys.stderr)
                return text
            except (urllib.error.URLError, urllib.error.HTTPError,
                    KeyError, IndexError, json.JSONDecodeError, TimeoutError) as exc:
                last_err = f"{model}: {exc}"
                print(f"[ai] {provider['base']} {last_err} — trying next", file=sys.stderr)
    print(f"[ai] ALL providers failed ({last_err})", file=sys.stderr)
    return None


HUNK_RE = re.compile(
    r"^<<<<<<<[^\n]*\n"          # <<<<<<< ours
    r"(?P<ours>.*?)"
    r"^(?:\|\|\|\|\|\|\|[^\n]*\n(?P<base>.*?))?"  # optional ||||||| base (diff3)
    r"^=======[^\n]*\n"          # =======
    r"(?P<theirs>.*?)"
    r"^>>>>>>>[^\n]*\n?",        # >>>>>>> theirs
    re.MULTILINE | re.DOTALL,
)


def strip_fences(text):
    text = text.strip("\n")
    m = re.match(r"^```[^\n]*\n(?P<body>.*?)\n?```$", text, re.DOTALL)
    if m:
        return m.group("body")
    return text


def resolve_file(path):
    with open(path, encoding="utf-8") as fh:
        content = fh.read()

    hunks = list(HUNK_RE.finditer(content))
    if not hunks:
        print(f"[ai] no conflict markers in {path} (skipping)", file=sys.stderr)
        return True

    print(f"[ai] {path}: {len(hunks)} conflict hunk(s)", file=sys.stderr)
    # Whole-file context the model needs to keep the result compilable:
    # the import block (symbols that actually exist) plus any removed-symbol
    # hints from OUR side (lines only in OURS are often fork patches).
    imports = [ln for ln in content.splitlines()
               if ln.lstrip().startswith(("import ", "package "))]
    import_block = "\n".join(imports)

    resolved = content
    # Replace from the end so earlier offsets stay valid.
    for match in reversed(hunks):
        start, end = match.span()
        before = resolved[:start].splitlines(keepends=True)[-CONTEXT_LINES:]
        after = resolved[end:].splitlines(keepends=True)[:CONTEXT_LINES]
        user = (
            f"File: {path}\n\n"
            f"--- file imports / package (only these symbols are guaranteed to exist) ---\n"
            f"{import_block}\n"
            f"--- context BEFORE the conflict ---\n{''.join(before)}\n"
            f"--- OURS (fork dev) ---\n{match.group('ours')}"
            f"--- THEIRS (upstream dev) ---\n{match.group('theirs')}"
            f"--- context AFTER the conflict ---\n{''.join(after)}\n"
            f"Output the resolved code for this conflict hunk now."
        )
        answer = call_llm(CONSTITUTION, user)
        if answer is None:
            return False
        replacement = strip_fences(answer)
        if re.search(r"^(<<<<<<<|=======|>>>>>>>)", replacement, re.MULTILINE):
            print(f"[ai] model emitted markers for {path} — rejecting", file=sys.stderr)
            return False
        if replacement and not replacement.endswith("\n"):
            replacement += "\n"
        resolved = resolved[:start] + replacement + resolved[end:]

    if re.search(r"^(<<<<<<<|=======|>>>>>>>)", resolved, re.MULTILINE):
        print(f"[ai] markers remain in {path} after resolution", file=sys.stderr)
        return False

    with open(path, "w", encoding="utf-8") as fh:
        fh.write(resolved)
    return True


def main():
    if len(sys.argv) < 2:
        print("usage: ai_resolve.py <file> [<file> ...]", file=sys.stderr)
        return 1
    ok = True
    for path in sys.argv[1:]:
        try:
            if not resolve_file(path):
                print(f"[ai] FAILED to resolve {path}", file=sys.stderr)
                ok = False
        except Exception as exc:  # binary files, decode errors, etc.
            print(f"[ai] error on {path}: {exc}", file=sys.stderr)
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
