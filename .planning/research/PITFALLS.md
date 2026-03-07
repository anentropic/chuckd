# Domain Pitfalls

**Domain:** GitHub Actions composite action wrapping a native binary CLI tool
**Project:** chuckd — schema evolution validator
**Researched:** 2026-03-07
**Confidence:** HIGH (based on direct codebase inspection + GitHub Actions specification knowledge)

---

## Critical Pitfalls

Mistakes that cause rewrites, broken action invocations, or security issues.

---

### Pitfall 1: Exit Code Conflation — Non-Boolean Return Value

**What goes wrong:** chuckd's `call()` method returns `issues.size()` — the count of incompatibilities found. On a runner with 256+ incompatibilities, the exit code wraps around to 0 (mod 256 on Linux), making the action silently pass a deeply broken schema. Even below 256, returning 5 is not the same as returning 1 — some CI systems treat non-zero/non-one codes as infrastructure failures rather than test failures.

**Why it happens:** Java's `System.exit(n)` maps directly to the shell exit code. The current code uses `issues.size()` which is semantically meaningful as a number, but exit codes should be 0 (success) or non-zero (failure). Most action consumers expect exactly 0 or 1.

**Consequences:** False passes when many incompatibilities exist (count wraps); confusing CI status messages when exit code is 3 or 7 instead of 1.

**Prevention:** Before writing the composite action shell script, verify what exit codes chuckd actually returns and whether they are safe. If `issues.size()` is the exit code, the wrapper script must normalize: `chuckd ... || exit 1`. Alternatively, add a `-o JSON` flag and parse the output to determine pass/fail separately from the exit code.

**Detection warning signs:**
- Smoke tests pass with exit code 1 for single-incompatibility case (fine)
- If you test with a schema that has 2+ incompatibilities, check `echo $?` — if it's `2` or higher, the issue is live
- Look at `ChuckD.java` line 178: `return issues.size()` confirms this behavior

**Phase:** Action implementation — normalize exit code in the shell wrapper step.

---

### Pitfall 2: Platform Detection Mismatch — Wrong Binary Downloaded

**What goes wrong:** The composite action must download the right binary for the runner. GitHub exposes `runner.os` (`Linux`, `macOS`, `Windows`) and `runner.arch` (`X64`, `ARM64`). The release artifacts use a different naming scheme: `Linux-x86_64`, `Linux-aarch64`, `macOS-aarch64`. If the mapping is wrong, the action downloads the wrong binary or fails to find one.

**Why it happens:** GitHub's `runner.arch` values (`X64`, `ARM64`) don't match the artifact naming convention (`x86_64`, `aarch64`). This requires explicit mapping in the action's shell script.

**Consequences:** Download of wrong binary (likely fails to execute); confusing error message ("exec format error" or "no such file or directory") that doesn't explain the root cause; macOS Intel runners would get a missing-artifact 404 since `macOS-x86_64` was discontinued after v0.5.x.

**Prevention:**
1. In the action's download step, explicitly map GitHub context variables to artifact names:
   ```bash
   case "${{ runner.os }}-${{ runner.arch }}" in
     Linux-X64)   PLATFORM="Linux-x86_64" ;;
     Linux-ARM64) PLATFORM="Linux-aarch64" ;;
     macOS-ARM64) PLATFORM="macOS-aarch64" ;;
     *)           echo "Unsupported platform: ${{ runner.os }}-${{ runner.arch }}"; exit 1 ;;
   esac
   ```
2. Fail fast with a clear error message for unsupported platforms (macOS Intel, Windows) rather than downloading a wrong binary silently.

**Detection warning signs:**
- Any test on a platform not in the explicit mapping list produces "exec format error"
- 404 from GitHub releases API means the platform string is wrong

**Phase:** Action implementation — platform detection logic. Test on both `ubuntu-latest` (X64) and `ubuntu-24.04-arm` (ARM64).

---

### Pitfall 3: action.yml Must Live at Repo Root

**What goes wrong:** The `action.yml` file must be at the repository root for the action to be referenceable as `uses: anentropic/chuckd@v1`. If placed in a subdirectory (e.g., `action/action.yml`), users must write `uses: anentropic/chuckd/action@v1`, which is non-standard and harder to discover in the marketplace.

**Why it happens:** The project decision to keep the action in the same repo as the CLI source might tempt placing `action.yml` in a subdirectory to separate concerns. This breaks marketplace discoverability.

**Consequences:** Non-standard reference path breaks convention; action won't appear with the canonical repo reference in the marketplace listing; users who expect `uses: anentropic/chuckd@v1` get a "file not found" error.

**Prevention:** Place `action.yml` at repo root. Composite actions can reference scripts in subdirectories (e.g., `runs.steps[*].run` can call `scripts/download-chuckd.sh`), so the implementation can still be organized in subdirectories — only `action.yml` must be at root.

**Detection warning signs:**
- If you run `gh api repos/anentropic/chuckd/contents/action.yml` and get a 404, the file is misplaced
- Marketplace submission will warn or fail if `action.yml` is not at root

**Phase:** Initial action scaffold — create `action.yml` at repo root from the start.

---

### Pitfall 4: Version Tag Collision — CLI Release Tags Used for Action References

**What goes wrong:** chuckd uses bare semver tags like `0.6.0` for releases. If the action is in the same repo, users referencing `uses: anentropic/chuckd@v1` need a stable major-version tag. The existing release workflow pushes bare semver tags (e.g., `0.6.0`). If `v1` is just a moving tag pointing to the latest `0.x.x`, it must be updated on every release — easy to forget.

**Why it happens:** CLI releases and action version tags serve different purposes. The CLI uses exact version tags for binary downloads. The action conventionally uses `v1`, `v2` major-version floating tags. In a shared repo, both naming schemes coexist and can cause confusion.

**Consequences:** `uses: anentropic/chuckd@v1` breaks when `v1` tag is never created or not updated after a new release; users can't pin to a stable action version.

**Prevention:**
1. Decide on an action version tag strategy before publishing: either use `@v1` as a floating tag (update it on every release) or document that users should use `@0.6.0` style pinning.
2. Update the `detect-release` workflow (or `build-release.yml`) to also move the `v1` tag after a successful release.
3. Standard convention is `v1` as a floating tag pointing to latest `1.x.x` release. Since chuckd is at `0.x.x`, consider starting the action at `v1` regardless and committing to it.

**Detection warning signs:**
- After publishing, test `uses: anentropic/chuckd@v1` in a real workflow — if it fails with "reference not found", the tag strategy is broken
- Check if `git tag -l 'v*'` shows any major-version tags after release

**Phase:** Release process planning — must be decided before marketplace submission.

---

### Pitfall 5: Composite Action Inputs Have No Type Safety

**What goes wrong:** Composite action `inputs` are always strings. There is no boolean, integer, or path type in the `action.yml` spec. When users pass `compatibility: BACKWARD`, if the action script does not validate this against the allowed enum values (`BACKWARD`, `FORWARD`, `FULL`, `BACKWARD_TRANSITIVE`, `FORWARD_TRANSITIVE`, `FULL_TRANSITIVE`), chuckd will print a cryptic picocli error message and the action step will fail with an unhelpful log entry.

**Why it happens:** The GitHub Actions input system has no schema validation. All inputs arrive as environment variables (strings). Defensive coding is entirely the action author's responsibility.

**Consequences:** Users who typo `BACKWARDS` (a common mistake) get picocli's internal error rather than a helpful action-level message like "Invalid compatibility value. Valid values are: BACKWARD, FORWARD..."

**Prevention:** Add input validation in the wrapper script before invoking chuckd:
```bash
case "$INPUT_COMPATIBILITY" in
  BACKWARD|FORWARD|FULL|BACKWARD_TRANSITIVE|FORWARD_TRANSITIVE|FULL_TRANSITIVE) ;;
  *) echo "Error: Invalid compatibility value '$INPUT_COMPATIBILITY'. Valid: BACKWARD FORWARD FULL BACKWARD_TRANSITIVE FORWARD_TRANSITIVE FULL_TRANSITIVE"; exit 1 ;;
esac
```

**Detection warning signs:**
- Test the action with an intentionally wrong `compatibility` value — if the error message is picocli's help text rather than an action-friendly message, validation is missing

**Phase:** Action implementation — input validation in the shell script.

---

### Pitfall 6: Git Checkout Depth Breaks Base-Branch Schema Comparison

**What goes wrong:** The "git-based comparison" mode (PR schema vs base branch) requires `git show origin/main:path/to/schema.json` or `git checkout` of the base branch schema. By default, most workflows use `actions/checkout@v4` with `fetch-depth: 1` (shallow clone). A shallow clone cannot access commits from the base branch or export files from them.

**Why it happens:** Shallow clones are the default because they're fast. Action users who don't read the docs carefully will use the default checkout and then wonder why `git show` returns "fatal: not a valid object name".

**Consequences:** The git-comparison mode silently fails or errors; users get confusing git errors rather than a schema compatibility result.

**Prevention:**
1. Document in the action's `README` and `description` that git-comparison mode requires `fetch-depth: 0` in the `actions/checkout` step.
2. In the action's shell script, detect the shallow-clone state before attempting git operations: `git rev-parse --is-shallow-repository` returns `true` if shallow. Fail with a helpful message.
3. Example guidance to include in docs:
   ```yaml
   - uses: actions/checkout@v4
     with:
       fetch-depth: 0  # Required for chuckd git-comparison mode
   ```

**Detection warning signs:**
- Test in a fresh repo where checkout uses default depth — any `git show` call will fail
- `git rev-parse --is-shallow-repository` returns `true` in shallow checkout

**Phase:** Action implementation + documentation. The `fetch-depth: 0` requirement should be prominent in the action README.

---

## Moderate Pitfalls

### Pitfall 7: Binary Caching Not Implemented — Slow Action on Every Run

**What goes wrong:** Each invocation downloads the chuckd binary from GitHub releases. On a busy monorepo, this adds 5-15 seconds per run and counts against GitHub's rate limits for unauthenticated API access to `api.github.com` and download limits.

**Prevention:** Use `actions/cache` with a cache key based on the chuckd version input. Cache the extracted binary between runs:
```yaml
- uses: actions/cache@v4
  with:
    path: ~/.local/bin/chuckd
    key: chuckd-${{ inputs.version }}-${{ runner.os }}-${{ runner.arch }}
```
If version is `latest`, the cache key must incorporate the actual resolved version tag to avoid stale binaries.

**Phase:** Action implementation (can be deferred to v1.1 if speed is acceptable).

---

### Pitfall 8: `latest` Version Resolution Is Fragile

**What goes wrong:** If the action accepts `version: latest` as an input (to always use the newest chuckd release), it must resolve the latest tag from the GitHub API. This resolution can fail if: (a) the GitHub API is rate-limited, (b) the release is still a draft, or (c) network issues in the runner.

**Prevention:**
- Default the version input to `latest` but document that pinning to a specific version (e.g., `version: "0.6.0"`) is more reliable in production.
- Use `https://github.com/anentropic/chuckd/releases/latest/download/...` redirect URLs rather than resolving via API — GitHub automatically redirects these to the actual latest release asset URL.
- Never default to resolving `latest` at action runtime without a fallback.

**Phase:** Action implementation — binary download step.

---

### Pitfall 9: Schema File Paths Must Be Relative to Workspace

**What goes wrong:** chuckd takes file paths as positional arguments. In a GitHub Actions context, files exist relative to `$GITHUB_WORKSPACE`. If users provide paths that don't account for the workspace root, chuckd reports "file not found" rather than a compatibility result.

**Prevention:**
- Document that inputs like `new-schema` and `previous-schemas` should be repo-relative paths (e.g., `schemas/person/current.json`).
- In the wrapper script, prefix paths with `$GITHUB_WORKSPACE` or run chuckd from that directory:
  ```bash
  cd "$GITHUB_WORKSPACE"
  chuckd "${{ inputs.new-schema }}" ${{ inputs.previous-schemas }}
```

**Phase:** Action implementation — document clearly in `action.yml` input descriptions and README.

---

### Pitfall 10: Marketplace Listing Requires Specific action.yml Fields

**What goes wrong:** Publishing to the GitHub Marketplace requires `name`, `description`, `author`, and `branding` (icon + color) in `action.yml`. Missing `branding` doesn't block functionality but results in a generic icon in the marketplace, reducing discoverability. Missing or vague `description` reduces search ranking.

**Prevention:** Include all required and recommended fields in `action.yml` from the start:
```yaml
name: 'chuckd Schema Compatibility Check'
description: 'Validate schema evolution compatibility for JSON Schema, Avro, and Protobuf schemas'
author: 'anentropic'
branding:
  icon: 'check-circle'
  color: 'green'
```

**Phase:** Action scaffold — include from day one, not as an afterthought.

---

### Pitfall 11: `GITHUB_TOKEN` vs `GH_TOKEN` Scoping for Binary Downloads

**What goes wrong:** Downloading release assets from a public GitHub repo doesn't require authentication — unauthenticated requests to `github.com/releases` work fine. However, if the action tries to use the GitHub API to resolve `latest` version and the workflow's default `GITHUB_TOKEN` has restricted permissions, API calls may fail in fork PRs (where the token has read-only access to public data but rate limits are shared).

**Prevention:**
- Use direct, unauthenticated download URLs for release assets (public repos don't need auth for downloads).
- If using the API to resolve `latest`, add an optional `github-token` input and use it only when provided.
- Never require `GITHUB_TOKEN` for basic functionality.

**Phase:** Action implementation — binary download step.

---

## Minor Pitfalls

### Pitfall 12: `inputs` Context in Composite Actions Requires Explicit `${{ inputs.name }}` Syntax

**What goes wrong:** In composite actions, you cannot use `$INPUT_NAME` shell-style environment variables directly in `run:` steps without explicitly setting `env:` with the input values. Unlike JavaScript actions, composite action inputs aren't automatically available as environment variables.

**Prevention:** Either use `${{ inputs.name }}` directly in shell commands, or set them explicitly:
```yaml
steps:
  - run: ./scripts/run-chuckd.sh
    env:
      NEW_SCHEMA: ${{ inputs.new-schema }}
      PREVIOUS_SCHEMAS: ${{ inputs.previous-schemas }}
```

**Phase:** Action implementation — first shell step written.

---

### Pitfall 13: Multiple Previous Schema Files as a Single Input String

**What goes wrong:** chuckd accepts multiple previous schema files: `chuckd new.json old1.json old2.json`. GitHub Actions inputs are single strings. If the action takes `previous-schemas` as a newline- or space-separated list, the wrapper script must split this correctly. Naive splitting breaks on file paths with spaces.

**Prevention:** Document that paths must be space-separated and must not contain spaces. Use `read -ra` for safer splitting:
```bash
read -ra PREV_SCHEMAS <<< "$PREVIOUS_SCHEMAS"
chuckd "$NEW_SCHEMA" "${PREV_SCHEMAS[@]}"
```
For v1, explicitly state "file paths must not contain spaces" in the action description.

**Phase:** Action implementation — argument passing to chuckd binary.

---

### Pitfall 14: macOS Gatekeeper Blocks Downloaded Binaries

**What goes wrong:** On macOS runners, Gatekeeper may block unsigned binaries downloaded from the internet. The chuckd binary is not code-signed with an Apple Developer certificate, so `xattr -d com.apple.quarantine` or similar steps may be required on macOS runners.

**Why it matters:** The action is intended for GitHub Actions runners (primarily Linux). macOS GitHub runners are available but less common. If a user tries the action on `macos-latest`, the binary may be quarantined.

**Prevention:**
- Scope v1 of the action to Linux runners only. State this clearly in the action README.
- If macOS support is needed later, add `xattr -d com.apple.quarantine /usr/local/bin/chuckd` after the binary is installed.

**Phase:** Action implementation — document supported platforms.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| action.yml scaffold | File must be at repo root; branding required for marketplace | Create at root, include all metadata fields from day one |
| Binary download step | Platform name mapping (X64 vs x86_64, ARM64 vs aarch64) | Explicit case/switch mapping in shell script; test both arches |
| Input handling | No type safety; multi-file inputs; workspace-relative paths | Validate enums in script; use `read -ra` for array splitting |
| Exit code handling | chuckd returns `issues.size()` not boolean | Normalize to `|| exit 1` in wrapper or check `[ $? -gt 0 ]` |
| Git comparison mode | Shallow checkout breaks git show | Detect shallow repo; document `fetch-depth: 0` requirement |
| Release process | Version tag strategy (v1 floating tag vs exact semver) | Decide before first publish; automate tag moving in CI |
| Marketplace submission | Missing required action.yml fields | Validate locally with `actionlint` before submitting |

---

## Sources

- Direct inspection of `ChuckD.java` (exit code behavior via `return issues.size()`)
- Direct inspection of `build-release.yml` (artifact naming: `chuckd-{Platform}-{Arch}-{version}.tar.gz`)
- Direct inspection of `README.md` (confirmed available platforms: Linux-x86_64, Linux-aarch64, macOS-aarch64)
- Direct inspection of `detect-release.yml` (current tag strategy: bare semver `0.6.0` format)
- GitHub Actions specification — composite action behavior (inputs as strings, no type coercion, `${{ inputs.name }}` required syntax)
- GitHub Actions specification — action.yml required fields for marketplace listing
- Confidence: HIGH for project-specific pitfalls (direct codebase inspection); HIGH for Actions behavior (stable well-documented spec)
