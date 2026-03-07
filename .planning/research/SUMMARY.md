# Project Research Summary

**Project:** chuckd GitHub Actions composite action
**Domain:** GitHub Actions composite action wrapping a native binary CLI schema validator
**Researched:** 2026-03-07
**Confidence:** HIGH

## Executive Summary

chuckd is an existing, working native binary schema compatibility validator (Java 21 + GraalVM, supporting JSON Schema, Avro, and Protobuf). The work to be done is narrow and well-defined: wrap it in a GitHub Actions composite action (`action.yml` at repo root) that downloads the correct pre-built release artifact, maps action inputs to CLI flags, and invokes the binary. The recommended approach is a pure composite shell action — no Docker, no Node.js, no build step for the action itself — because native binaries already exist for the three target platforms (Linux x86_64, Linux aarch64, macOS aarch64) and composite actions avoid container startup overhead entirely. This is a "download and run" pattern with established precedents in the ecosystem (cosign-installer, golangci-lint-action).

The primary value-add over a raw YAML snippet is ergonomic git-based schema comparison: the action should support a `base-ref` input that extracts the previous schema version from git history automatically, eliminating the need for users to maintain a separate "previous schema" file. This is the pattern buf-action uses for breaking-change detection on PRs and is the main differentiator for v1. Everything else — PR comment posting, auto-detection of changed files, Windows support, checksum verification, binary caching — is explicitly deferred or ruled out as an anti-feature for v1.

The two critical risks are: (1) chuckd's exit code is `issues.size()` rather than a boolean 0/1, which must be normalized in the wrapper shell script to avoid false-passes when incompatibility count wraps mod 256; and (2) git-compare mode requires `fetch-depth: 0` in the caller's checkout step, which conflicts with the shallow-clone default and must be documented prominently. Both risks have clear mitigations and neither blocks the implementation — they just need to be handled deliberately.

## Key Findings

### Recommended Stack

The action is a pure YAML composite action with inline bash steps. No new toolchain is introduced. The only new file is `action.yml` at the repo root. Inside the action, platform detection uses `runner.os` + `runner.arch` GitHub context variables (mapped explicitly to artifact naming: `Linux-X64` → `Linux-x86_64`, etc.), binary download uses `curl -fsSL` + `tar -xzf` targeting GitHub Releases URLs, and shell scripting uses `bash` (explicitly declared on every step — required for composite actions). The `actions/checkout` version used in existing workflows should be verified against actual release tags before reuse.

**Core technologies:**
- GitHub Actions composite action (`runs.using: composite`): action runtime — only viable type that avoids Docker overhead and requires no build step
- Bash (`shell: bash`): step execution — universally available on all GitHub-hosted runners; must be declared explicitly on every composite step
- `curl` + `tar`: binary download and extraction — standard Unix tooling, pre-installed on all GHA runners, no third-party action dependency
- `runner.os` / `runner.arch` context variables: platform detection — built-in GHA feature, maps to artifact naming scheme from `build-release.yml`
- `$RUNNER_TEMP`: binary installation directory — guaranteed writable, outside workspace, auto-cleaned after job

### Expected Features

**Must have (table stakes — v1):**
- Inputs: `new-schema`, `previous-schema`, `schema-format` (default: JSONSCHEMA), `compatibility` (default: FORWARD_TRANSITIVE), `version` (default: pinned stable release)
- Platform detection for Linux-x86_64, Linux-aarch64, macOS-aarch64 with explicit failure on unsupported platforms
- Binary download and extraction to `$RUNNER_TEMP/chuckd/`
- CLI invocation mapping action inputs to chuckd flags (`-f`, `-c`, positional args)
- Workflow failure on incompatibility (normalized exit code)
- Informative step log output (chuckd's default TEXT format is sufficient)
- Git-compare mode (`base-ref` input using `git show {base-ref}:{path}`) — the primary PR ergonomics differentiator
- Action metadata: `name`, `description`, `author`, `branding` — required for marketplace listing

**Should have (v1.x, after validation):**
- Multi-value `previous-schema` (newline-separated) — when users report needing transitive multi-version checks
- `output-format: json` input — for structured log aggregation pipelines
- `fail-on-incompatibility` boolean bypass — for audit/report-only mode

**Defer (v2+):**
- Binary checksum/SHA verification — requires publishing checksums alongside releases (new CI scope)
- Binary caching via `actions/cache` — premature optimization; measure first
- Windows runner support — requires new build matrix in `build-release.yml`, out of scope

**Explicit anti-features (never for v1):**
- PR comment posting — requires `write` permissions on pull-requests, breaks for fork PRs, ruled out in PROJECT.md
- Auto-detection of changed schema files — requires knowing repo structure and file naming; high false-positive risk, ruled out in PROJECT.md
- `latest` version auto-resolution — non-deterministic, adds GitHub API dependency; use pinned default version

### Architecture Approach

The composite action is a thin orchestration layer: it has no runtime of its own and executes inline inside the caller's runner. The execution sequence is linear and has no concurrency: (1) detect platform, set output variable; (2) download and extract binary to `$RUNNER_TEMP`; (3) optionally resolve schema file via `git show` for git-compare mode; (4) invoke chuckd binary with mapped arguments. All components live in `action.yml` at the repo root. A companion `test-action.yml` workflow validates the action itself in CI.

**Major components:**
1. `action.yml` (repo root) — composite action manifest: inputs, outputs, step sequence
2. Platform detection step — maps `runner.os`/`runner.arch` to artifact name; emits output variable; explicit failure for unsupported platforms
3. Binary download step — constructs GitHub Releases URL from platform + version inputs; downloads and extracts to `$RUNNER_TEMP/chuckd/`
4. Schema resolution step (conditional) — extracts base-branch schema via `git show {base-ref}:{path}` into temp file when `base-ref` input is provided
5. CLI invocation step — maps action inputs to chuckd flags; normalizes exit code to 0/1; captures stdout to step log
6. `test-action.yml` workflow — integration test that exercises the action against real schemas

### Critical Pitfalls

1. **Exit code non-normalization** — chuckd returns `issues.size()` (count of incompatibilities), not boolean 0/1. With 256+ incompatibilities, exit code wraps to 0 (false pass). Mitigation: wrap invocation as `chuckd ... || exit 1` in the shell step, or normalize with `[ $? -gt 0 ] && exit 1`.

2. **Platform detection mismatch** — GitHub context uses `X64`/`ARM64` but release artifacts use `x86_64`/`aarch64`. Must be mapped explicitly with a `case` statement; no implicit string transformation is safe. Test on both `ubuntu-latest` (X64) and `ubuntu-24.04-arm` (ARM64).

3. **Shallow checkout breaks git-compare mode** — `actions/checkout` default `fetch-depth: 1` means `git show origin/main:path` fails with "not a valid object name". Document `fetch-depth: 0` requirement prominently. Add a shallow-repo detection check (`git rev-parse --is-shallow-repository`) before git operations.

4. **Version tag strategy collision** — existing release tags are bare semver (`0.6.0`), but action convention expects `v1` as a floating major-version tag. Must decide and automate tag-moving before marketplace submission. Without `v1` tag, `uses: anentropic/chuckd@v1` fails with "reference not found".

5. **Composite action input type safety** — all inputs are strings; no enum validation. A typo in `compatibility: BACKWARDS` produces a cryptic picocli error rather than an action-level message. Add explicit `case` statement validation before invoking chuckd.

## Implications for Roadmap

Based on research, the build order is dictated by the linear dependency chain identified in ARCHITECTURE.md. Phases map directly to that chain.

### Phase 1: Action Scaffold and Metadata

**Rationale:** `action.yml` must exist at repo root before any other component can be developed or tested. Metadata (name, branding, input declarations) must be complete for marketplace submission. This is the foundation everything else depends on and has zero implementation risk — it is pure YAML declaration.

**Delivers:** A syntactically valid `action.yml` with all inputs declared, correct metadata, and `runs.using: composite` structure. No functional logic yet, but the file exists in the right place.

**Addresses:** Table stakes features (action metadata, branding), Pitfall 3 (file must be at repo root), Pitfall 10 (marketplace required fields).

**Avoids:** Anti-Pattern 2 (subdirectory placement).

### Phase 2: Platform Detection and Binary Download

**Rationale:** The binary must be downloaded before it can be invoked. Platform detection is a prerequisite for URL construction. These two steps are tightly coupled and should be built together. This phase delivers a usable binary on the runner — the minimum required for any subsequent testing.

**Delivers:** Working shell steps that detect the runner platform, construct the correct GitHub Releases URL, download and extract the chuckd binary to `$RUNNER_TEMP/chuckd/`.

**Addresses:** Table stakes features (platform detection, version input, binary download).

**Avoids:** Pitfall 2 (platform name mismatch — explicit `case` statement required), Pitfall 9 (schema file paths relative to workspace), Anti-Pattern 3 (binary to workspace), Anti-Pattern 5 (silent platform failure).

**Note:** Defer `latest` version auto-resolution; pin a default version in `action.yml`.

### Phase 3: CLI Invocation and Exit Code Normalization

**Rationale:** Once the binary is available, wire up the input-to-flag mapping and handle exit codes correctly. This phase must address Pitfall 1 (exit code non-normalization) before the action can be safely used — a false pass is worse than no action at all. Add input validation for the `compatibility` enum here too.

**Delivers:** A working action in explicit-path mode: given two schema file paths, it validates compatibility and fails the workflow correctly on incompatibility. This is the minimum viable action.

**Addresses:** Fail-on-incompatibility, log output, input validation.

**Avoids:** Pitfall 1 (exit code wrapping — normalize to `|| exit 1`), Pitfall 5 (no type safety — add enum validation), Pitfall 12 (inputs context syntax — use `${{ inputs.name }}` explicitly), Pitfall 13 (multi-file input splitting).

### Phase 4: Git-Compare Mode

**Rationale:** Git-compare mode is the primary PR ergonomics differentiator identified in FEATURES.md and ARCHITECTURE.md. It requires the core invocation (Phase 3) to be working and correct. This phase adds the `base-ref` input and the `git show` schema extraction step.

**Delivers:** A `base-ref` input that extracts the previous schema version from git history automatically. Users no longer need to maintain a separate "previous schema" file for PR validation workflows.

**Addresses:** Git-compare mode differentiator, improved PR UX.

**Avoids:** Pitfall 6 (shallow checkout breaks git-compare — detect and provide helpful error, document `fetch-depth: 0`).

### Phase 5: Versioning Strategy and Release Process

**Rationale:** Before the action can be published or tested externally, the version tag strategy must be resolved. The existing release process uses bare semver tags; action convention expects `v1` as a floating tag. This decision and its automation must be in place before marketplace submission. Automate the `v1` tag update in the release workflow.

**Delivers:** Updated `detect-release.yml` (or `build-release.yml`) that moves the `v1` floating tag on every release. Clear documentation of the versioning strategy.

**Addresses:** Version pinning for callers.

**Avoids:** Pitfall 4 (version tag collision — `uses: anentropic/chuckd@v1` fails without this).

### Phase 6: Integration Testing

**Rationale:** The action must be validated end-to-end in CI before marketplace submission. The integration test exercises the action against real schemas across the three supported platforms. This is also where edge cases (wrong platform, shallow checkout in git-compare mode, invalid inputs) should be covered.

**Delivers:** `test-action.yml` workflow that validates the composite action against multiple scenarios (compatible schema, incompatible schema, git-compare mode, unsupported platform handling) on Linux x86_64 and ARM64 runners.

**Addresses:** Quality gate before publication, edge case coverage.

**Avoids:** All pitfalls — this is the verification phase.

### Phase Ordering Rationale

- Phases 1-3 are strictly sequential due to file-system dependencies (action.yml must exist, binary must be downloaded before invocation).
- Phase 4 (git-compare) is additive and independent of Phase 5 (versioning), but both should complete before Phase 6 (testing) to test the full feature set.
- Phase 5 (versioning) has no code dependencies but must be decided before external testing or publication.
- Phase 6 (integration testing) closes the loop and gates marketplace submission.

### Research Flags

Phases with well-documented patterns — skip dedicated research-phase:
- **Phase 1 (scaffold):** Composite action YAML structure is fully specified; patterns are clear.
- **Phase 2 (platform detection + download):** `runner.os`/`runner.arch` mapping and `curl`+`tar` pattern are high-confidence and well-established.
- **Phase 3 (CLI invocation):** chuckd CLI interface is directly inspected; flags and exit codes are confirmed.

Phases that may benefit from a targeted research check:
- **Phase 4 (git-compare mode):** The `git show` approach is straightforward, but the shallow-clone detection behavior (`git rev-parse --is-shallow-repository`) on specific runner configurations is worth a quick validation. LOW risk overall.
- **Phase 5 (versioning):** The `actions/checkout` version in use (`@v6` in existing workflows) should be verified against actual GitHub releases before reuse. The floating `v1` tag update mechanism in CI needs one look at existing `detect-release.yml` logic.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Composite action pattern is definitive; curl+tar universally available; runner context vars are stable API. One LOW point: `actions/checkout` version in existing workflows (`@v6`) needs verification. |
| Features | HIGH | Derived from direct inspection of 5 real published actions (buf-action, cosign-installer, trivy-action, conftest-action, golangci-lint-action) plus direct chuckd CLI inspection. Feature decisions are clear. |
| Architecture | HIGH | Linear dependency chain is unambiguous. All components inspected directly from existing codebase. Composite action structure is well-established. |
| Pitfalls | HIGH | Critical pitfalls derived from direct codebase inspection (exit code behavior confirmed in `ChuckD.java`, artifact naming confirmed in `build-release.yml`, tag strategy confirmed in `detect-release.yml`). GHA-specific pitfalls from stable documented spec. |

**Overall confidence:** HIGH

### Gaps to Address

- **`actions/checkout` version:** Existing workflows use `@v6`. Canonical current version is v4. Verify actual release tags on `github.com/actions/checkout` before writing `action.yml`. Use whichever tag is confirmed current.
- **`latest` version default behavior:** Research recommends against `latest` auto-resolution. The `version` input default should be hardcoded to the current stable release (e.g., `0.6.0`). This default will need updating with each chuckd release — decide whether to automate this update in the release workflow.
- **macOS Gatekeeper:** The macOS aarch64 binary may require `xattr -d com.apple.quarantine` on macOS GitHub runners. Scope v1 to Linux runners and document macOS as best-effort until this is tested.
- **`actions/checkout` version in action.yml:** If the action uses `actions/checkout` internally (for git-compare mode, if a checkout step is added), confirm the correct version tag before wiring it up.

## Sources

### Primary (HIGH confidence)

- Direct inspection of `ChuckD.java` — CLI flags, exit code behavior (`return issues.size()`)
- Direct inspection of `build-release.yml` — artifact naming scheme, platform matrix, release process
- Direct inspection of `detect-release.yml` — current version tag strategy (bare semver `0.6.0`)
- Direct inspection of `bat-tests/smoke.bats` — binary invocation patterns
- Direct inspection of `bufbuild/buf-action` action.yml (fetched live 2026-03-07) — inputs design, git-compare pattern
- Direct inspection of `sigstore/cosign-installer` action.yml (fetched live 2026-03-07) — composite action download pattern
- Direct inspection of `aquasecurity/trivy-action` action.yaml (fetched live 2026-03-07) — inputs design, version handling
- Direct inspection of `instrumenta/conftest-action` action.yml (fetched live 2026-03-07) — negative example (Docker-based)
- Direct inspection of `reviewdog/action-golangci-lint` action.yml (fetched live 2026-03-07) — multi-input patterns
- `.planning/PROJECT.md` — scope decisions, constraints, out-of-scope list

### Secondary (MEDIUM confidence)

- GitHub Actions composite action documentation (training data, knowledge cutoff August 2025) — `shell: bash` requirement, `${{ inputs.name }}` syntax, `$RUNNER_TEMP`, `runner.os`/`runner.arch` context values
- `.planning/codebase/STACK.md`, `.planning/codebase/INTEGRATIONS.md` — existing stack and release artifact format

### Tertiary (LOW confidence)

- `actions/checkout` version: existing workflows use `@v6` which may be non-canonical; verify against actual releases before use

---
*Research completed: 2026-03-07*
*Ready for roadmap: yes*
