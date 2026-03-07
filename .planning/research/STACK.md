# Technology Stack

**Project:** chuckd GitHub Actions composite action
**Researched:** 2026-03-07
**Scope:** Stack additions needed for the new composite action feature only. Does not re-cover the existing Java/GraalVM/Gradle CLI stack.

---

## What This Feature Requires

A GitHub Actions composite action is a pure YAML file (`action.yml`) that orchestrates shell steps. It downloads a pre-built native binary from GitHub Releases and invokes it. There is no new language runtime, no new build system, and no new compilation step needed.

**New artefacts:**
- `action.yml` — composite action definition (the only new file that matters)
- Optional: `MAINTAINER.md` or docs update for action users

**Existing infrastructure that is reused:**
- Native binaries already published to GitHub Releases as `.tar.gz` archives
- Artifact naming convention already established: `chuckd-{Linux-x86_64,Linux-aarch64,macOS-aarch64}-{version}.tar.gz`
- GitHub token permissions model already understood from existing workflows

---

## Recommended Stack (New Additions Only)

### Composite Action Runtime

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| GitHub Actions composite action | N/A (platform feature) | Action type | Only viable option that avoids Docker startup overhead and uses existing native binaries. Docker action would require a base image and is slower on cold start. JavaScript action would add a Node.js dependency and build step. |
| Bash (via `runs: using: composite`) | Shell built into runner | Step execution | Standard shell universally available on all GitHub-hosted runners. `bash` is the correct `shell:` value for composite action steps — `sh` lacks some features, PowerShell is Windows-only. |

### GitHub Actions Used Inside the Action

| Action | Version | Purpose | Why |
|--------|---------|---------|-----|
| `actions/checkout@v4` | v4 | Checkout repo for git-based schema comparison (base branch version) | Required when user wants to compare PR schema vs base branch schema. v4 is current major; existing repo uses v6 which appears to be a non-canonical version — verify this. Confidence: MEDIUM. |
| No additional download action | — | Binary download | Use `curl` + `tar` inline shell steps rather than a third-party download action. Avoids an extra dependency, simpler, and the pattern is standard in the ecosystem. |

**Note on `actions/checkout` version:** The existing workflows in this repo use `actions/checkout@v6`. As of March 2026, the official GitHub-maintained current version is v4. `@v6` may be a pre-release or non-existent tag — this warrants verification against the actual releases at `https://github.com/actions/checkout/releases`. If `@v6` works in existing CI, it exists; but the action.yml for the new composite action should use whatever version is confirmed current. Confidence: LOW on exact version — verify before committing.

### Platform Detection (Inside the Action)

| Mechanism | Why |
|-----------|-----|
| `runner.os` + `runner.arch` GitHub Actions context variables | Built-in, no external action needed. `runner.os` returns `Linux` or `macOS`. `runner.arch` returns `X64` or `ARM64`. Map these to artifact names: `Linux+X64` → `Linux-x86_64`, `Linux+ARM64` → `Linux-aarch64`, `macOS+ARM64` → `macOS-aarch64`. Confidence: HIGH — documented GitHub Actions context. |
| Inline `if:` conditionals per step or shell `case` statement | Keeps platform branching inside the composite action itself, no extra action needed. |

### Binary Download Pattern

| Mechanism | Why |
|-----------|-----|
| `curl -fsSL` + `tar -xzf` in a `run:` shell step | Standard approach. No action dependency, no Node.js required, universally available on all GitHub-hosted runners. GitHub Releases download URLs are stable and predictable. Confidence: HIGH. |
| GitHub Releases API URL format: `https://github.com/anentropic/chuckd/releases/download/{tag}/{filename}` | Existing artifact naming convention (`chuckd-{platform}-{version}.tar.gz`) is already established by `build-release.yml`. The action needs the `version` input to construct this URL. |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Action type | Composite | Docker action | Docker action has cold-start overhead (image pull) and requires maintaining a base image or using the published Docker Hub image. Existing native binaries make composite the better choice. Already decided in PROJECT.md. |
| Action type | Composite | JavaScript/TypeScript action | Adds Node.js build step, `node_modules`, compilation to dist/ — significant complexity overhead for what is essentially a download-and-run task. |
| Binary download | `curl` + `tar` inline shell | `robinraju/release-downloader` or similar | Third-party download actions add a dependency with its own versioning and maintenance burden. The direct `curl` approach is simpler and more transparent. |
| Binary download | `curl` + `tar` inline shell | `actions/download-artifact` | That action is for artifacts from the same workflow run, not from GitHub Releases. Wrong tool. |
| Platform detection | `runner.os`/`runner.arch` context vars | Custom detection script | Context vars are the official supported mechanism. No reason to use `uname` when the platform is already known. |
| Checkout for git comparison | `actions/checkout@v4` (with `ref: ${{ github.base_ref }}`) | Manual `git fetch`/`git checkout` | The checkout action handles auth, sparse checkout, and other edge cases correctly. |

---

## What NOT to Add

| Item | Reason |
|------|--------|
| Node.js toolchain or `package.json` | Not a JavaScript action. Adding npm/yarn/Node would be pure overhead. |
| New build workflow for the action itself | Composite actions are plain YAML — no compilation, no build step, no dist/ directory. |
| `actions/setup-java` or `graalvm/setup-graalvm` | The native binary is self-contained. No JVM is needed at action runtime. |
| Docker as the action runtime | Already ruled out in PROJECT.md. Native binary is faster and already exists. |
| `softprops/action-gh-release` | That's for publishing releases, not consuming them. The action is a consumer. |
| Separate repository for the action | PROJECT.md explicitly rules this out. Same repo is simpler. |

---

## Action Inputs Design (Informs `action.yml` Structure)

These are not external dependencies but define what the action.yml `inputs:` block needs, which affects implementation choices:

| Input | Required | Purpose |
|-------|----------|---------|
| `version` | No (defaults to `latest`) | Which chuckd release to download. Using `latest` requires a GitHub API call to resolve; pinning a version avoids this. |
| `schema-type` | Yes | `json-schema`, `avro`, or `protobuf` — maps to `-f` flag |
| `compatibility-mode` | Yes | `BACKWARD`, `FORWARD`, `FULL`, etc. — maps to `-c` flag |
| `previous-schema` | Yes | Path to the previous/base schema file |
| `current-schema` | Yes | Path to the new/current schema file |

**`latest` version resolution:** If `version: latest` is supported, the action must call the GitHub API (`https://api.github.com/repos/anentropic/chuckd/releases/latest`) to get the actual tag. This requires the `github.token` input (or uses `GITHUB_TOKEN` env var automatically available). This is a MEDIUM complexity addition — worth supporting for ease of use, but the implementation requires a `curl` to the API and `jq` to parse the response. `jq` is pre-installed on all GitHub-hosted runners. Confidence: HIGH.

---

## GitHub Token Permissions

The composite action itself needs no special permissions beyond what the calling workflow already has. Downloading public release artifacts from `github.com/anentropic/chuckd/releases` does not require authentication (the repo is public). The `GITHUB_TOKEN` is only needed if resolving `latest` via the API (to avoid rate limiting on unauthenticated calls).

---

## File Structure for the Action

```
/action.yml          ← composite action definition (new)
```

The action.yml goes in the repo root. GitHub Actions requires it at the root or at a path specified in a `uses:` reference. For a same-repo action consumed as `anentropic/chuckd@v1`, it must be at the root. Confidence: HIGH — documented GitHub Actions behavior.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Composite action as correct type | HIGH | Official GHA docs confirm this is the right approach for "download and run" actions |
| `runner.os` / `runner.arch` context vars | HIGH | Long-standing documented GHA feature, stable API |
| `curl` + `tar` for download | HIGH | Standard Unix tooling, universally present on GHA runners |
| `actions/checkout` version | LOW | Existing repo uses `@v6` which may be non-canonical — verify actual current release tag |
| `jq` availability on runners | HIGH | Pre-installed on all GitHub-hosted runners (ubuntu, macos) for years |
| `latest` version resolution via GitHub API | MEDIUM | Technically straightforward, but adds a network call and mild complexity |

---

## Sources

- Existing `build-release.yml`: artifact naming convention confirmed — `chuckd-{macOS-aarch64,Linux-x86_64,Linux-aarch64}-{version}.tar.gz`
- Existing `test-build.yml`, `detect-release.yml`, `homebrew.yml`: existing GHA action versions and patterns
- `.planning/PROJECT.md`: constraints (composite not Docker, same repo, native binary from releases, Linux x86_64 + aarch64 platforms)
- `.planning/codebase/STACK.md`: existing stack (Java 21, GraalVM, Gradle)
- `.planning/codebase/INTEGRATIONS.md`: release artifact format and hosting confirmed
- GitHub Actions composite action documentation (official): `https://docs.github.com/en/actions/sharing-automations/creating-actions/creating-a-composite-action` — not directly fetched in this session (WebFetch unavailable), relied on training data + existing workflow patterns. Confidence: MEDIUM on GHA-specific details.
