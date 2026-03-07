# Feature Research

**Domain:** GitHub Actions composite action for a CLI schema-compatibility validator
**Researched:** 2026-03-07
**Confidence:** HIGH (derived from direct inspection of real published actions — buf-action, trivy-action, cosign-installer, golangci-lint-action — plus the chuckd CLI interface and PROJECT.md constraints)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features that users assume exist when they adopt an action from the marketplace. Missing
any of these causes the action to feel broken or untrustworthy.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Inputs for schema file paths** | Every CI tool action takes configuration inputs; users expect to configure what to validate | LOW | `new-schema` and `previous-schema` as explicit file path inputs. Positional args in the CLI become named inputs in the action. Mirrors the CLI: `chuckd <new> <prev...>` |
| **`schema-format` input** | The CLI requires `-f` (JSONSCHEMA/AVRO/PROTOBUF); users need to set this | LOW | Maps directly to the `-f` flag. Reasonable default: `JSONSCHEMA` (same as CLI default). |
| **`compatibility` input** | The CLI requires `-c`; users need to specify their compatibility mode | LOW | Maps directly to `-c` flag. Default: `FORWARD_TRANSITIVE` (same as CLI default). |
| **Fail the workflow on incompatibility** | Every CI validation action must fail the step/job when it finds a problem | LOW | chuckd already exits with a nonzero code equal to the count of issues. Composite action inherits this automatically — no extra logic needed. |
| **Informative log output** | Users expect to see what went wrong, not just a red X | LOW | chuckd's TEXT output (the default) prints incompatibility details to stdout, which GitHub Actions captures in the step log. No extra work required — this is already wired up. |
| **Platform detection for binary download** | Users run actions on standard runners (ubuntu-latest = Linux x86_64, ubuntu-24.04-arm = Linux ARM64); the action must "just work" | MEDIUM | Must detect `runner.os` + `runner.arch` and map to the correct release artifact. Three platforms: Linux-x86_64, Linux-aarch64, macOS-aarch64. Explicit failure message for unsupported platforms (e.g. Windows). |
| **`version` input with a sensible default** | Users expect to pin a version or use a recent default; no version input means the action is unpredictable | LOW | Default to the current stable release. "latest" auto-resolution is optional (see Differentiators). Without this, callers cannot control which binary runs against their schemas. |
| **Branding (`name`, `description`, `author` in action.yml)** | Marketplace listing requires these; poorly described actions get low adoption | LOW | Required metadata fields. `branding.icon` and `branding.color` are optional but expected by power users who browse the marketplace. |
| **Explicit `shell: bash` on every composite step** | A composite action without `shell:` declarations fails at runtime — this is a GHA requirement for composite actions, not workflow jobs | LOW | Technical requirement, not a user-facing feature, but missing it means the action simply doesn't run. This is a known sharp edge in composite action authoring. |

---

### Differentiators (Competitive Advantage)

Features that make chuckd's action better than "just write a download-and-run workflow
step yourself." These are what justify publishing to the marketplace rather than
documenting a raw YAML snippet.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Git-compare mode** | Users on PRs often want to compare the changed schema against the base-branch version without manually managing a "previous version" file. `git show base-branch:path/to/schema` extracts it automatically. | MEDIUM | Uses `git show {base-ref}:{schema-path}` to extract the old schema into a temp file in `$RUNNER_TEMP`. Requires that `actions/checkout` has already run in the calling workflow (which is standard). The action should document this clearly. Maps to the PROJECT.md "Support git-based comparison" requirement. |
| **`previous-schema` as multi-value input** | chuckd supports checking against multiple previous versions (oldest→newest); the action should expose this rather than limiting to one | MEDIUM | Action inputs are strings; a newline-separated list (same pattern as buf-action's `paths` input) is the idiomatic way. Shell splits on newlines to build the argument list. |
| **JSON output mode option** | Teams with structured log aggregation (Datadog, Splunk, etc.) want machine-readable output they can parse from the step log | LOW | Expose `output-format: json` input mapping to `-o JSON`. Most users won't need it, but it costs nothing to expose. |
| **Checksum/SHA verification of downloaded binary** | Security-conscious teams (and orgs with policy enforcement) expect the action to verify the downloaded binary hasn't been tampered with | HIGH | Requires publishing checksums alongside release tarballs (new CI step in build-release.yml) and verifying with `sha256sum` in the action. Significant scope increase for v1 — defer unless explicitly required by adopters. |
| **Clear error message on unsupported platform** | Better than a cryptic 404 or "binary not found" error | LOW | One `case` statement in shell with `echo "Unsupported platform: $OS/$ARCH — chuckd supports Linux x86_64, Linux ARM64, macOS ARM64" && exit 1`. Small effort, high clarity payoff. |
| **`skip` label support** | buf-action lets teams apply a `buf skip breaking` PR label to bypass checks. Useful for intentional breaking changes that are migration-tracked. | LOW | An optional `fail-on-incompatibility` boolean input (default `true`) or label-check logic. For v1, just document that users can add `continue-on-error: true` to the step. No action-level support needed. |

---

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem like natural additions but create maintenance burden, complexity, or
scope creep inconsistent with the project's goals.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **PR comment posting with incompatibility details** | buf-action and other tools post structured comments to PRs; users naturally expect this | Requires `write` permissions on `pull-requests`, forks cannot post comments without PAT, adds GITHUB_TOKEN dependency, and significantly complicates the action. PROJECT.md explicitly rules this out for v1. The step log is sufficient. | Use `continue-on-error: true` + a separate job step to post a comment if teams truly need it. Keep the action itself pure. |
| **Auto-detection of changed schema files** | Users don't want to configure paths manually; tools like `dorny/paths-filter` do this for other file types | Requires knowing the user's repo structure, file naming conventions, and schema format. Non-trivial to implement correctly for all three schema formats. High false-positive/negative risk. PROJECT.md explicitly rules this out. | Document the use of `dorny/paths-filter` or `tj-actions/changed-files` in the calling workflow to conditionally invoke the action. |
| **Schema registry integration** | Confluent Schema Registry users might want the action to push/pull schemas from a live registry | This is a fundamentally different use case — chuckd's value is offline validation without a registry. Adding registry integration re-implements what the Confluent CLI already does and muddies the tool's identity. | Point users to the Confluent CLI GitHub Action for registry workflows. |
| **`latest` version auto-resolution** | Users dislike pinning versions; "give me the latest" is the path of least resistance | Adds a GitHub API call (rate-limited for unauthenticated requests in high-volume CI), adds `jq` dependency (present on runners but an implicit assumption), and makes the action non-deterministic across runs. | Default the `version` input to a recent stable release. Document how to override. Update the default with each chuckd release. |
| **Windows runner support** | Teams running on Windows runners would benefit | No Windows binary is published (PROJECT.md constraint — existing release artifacts only). Adding Windows support requires a new build matrix in build-release.yml, which is out of scope for this milestone. | Document Windows as explicitly unsupported. Suggest Docker-based workaround (the Docker image supports linux/amd64 via WSL2). |
| **Caching downloaded binary between runs** | Reduces per-invocation download time (~5-10s) | `actions/cache` requires a `key` tied to the binary version, which adds complexity. The download is fast (native binary ~5MB). Cache eviction and invalidation edge cases add debugging burden. Premature optimization for v1. | Revisit after measuring actual invocation time in production use. |
| **Separate composite action repo** | Some marketplace authors prefer a standalone repo for cleaner versioning | PROJECT.md explicitly rules this out. Same-repo is simpler to maintain and keeps action versioning aligned with CLI releases. | Stay in the same repo. Use major-version tags (`v1`) that float. |
| **Docker-based action variant** | Some CI environments prefer Docker for hermetic execution | Slower startup, requires base image maintenance, conflicts with the performance rationale for native binaries. Already decided against in PROJECT.md. | Native binary via composite action. |

---

## Feature Dependencies

```
[Inputs: new-schema, previous-schema, schema-format, compatibility]
    └──required by──> [CLI invocation step]
                          └──required by──> [Fail on incompatibility]
                                               └──required by──> [Log output to Actions UI]

[Platform detection]
    └──required by──> [Binary download step]
                          └──required by──> [CLI invocation step]

[`version` input]
    └──required by──> [Binary download step URL construction]

[Git-compare mode]
    └──requires──> [actions/checkout already run in caller's workflow]
    └──requires──> [CLI invocation step] (produces the temp file that replaces previous-schema arg)
    └──enhances──> [PR workflow ergonomics]

[JSON output mode]
    └──enhances──> [Log output to Actions UI]
    └──independent of──> [Git-compare mode]

[Unsupported platform error message]
    └──required by──> [Platform detection] (must handle the else branch)

[multi-value previous-schema]
    └──enhances──> [CLI invocation step] (builds argument list from newline-split input)
    └──independent of──> [Git-compare mode]
```

### Dependency Notes

- **Platform detection requires resolution before binary download:** The artifact name is
  `chuckd-{platform}-{version}.tar.gz`; platform must be known before the URL can be
  constructed. These must be separate steps (or a single combined step that sets an
  output variable).

- **Binary download must complete before CLI invocation:** The binary does not exist
  before the download step. Trivially sequential.

- **Git-compare mode is optional/additive:** It is an alternative way to provide the
  `previous-schema` argument. It does not block the explicit-path mode. The action
  can support both: if `base-ref` input is set, use git-compare mode; otherwise use
  `previous-schema` path input directly.

- **`version` input and binary URL are tightly coupled:** Any change to the release
  asset naming convention in `build-release.yml` is a breaking change to the action.
  The ARCHITECTURE.md already calls out keeping these in sync.

---

## MVP Definition

### Launch With (v1)

Minimum viable product — the simplest action that provides genuine value over "paste a
wget snippet in your workflow."

- [ ] `action.yml` with `name`, `description`, `author`, `branding`
- [ ] Inputs: `new-schema` (required), `previous-schema` (required), `schema-format` (default: JSONSCHEMA), `compatibility` (default: FORWARD_TRANSITIVE), `version` (default: pinned stable release)
- [ ] Platform detection for Linux-x86_64, Linux-aarch64, macOS-aarch64 with explicit failure on unsupported platforms
- [ ] Binary download + extraction to `$RUNNER_TEMP`
- [ ] CLI invocation mapping action inputs to chuckd flags
- [ ] Fails the step (and therefore the PR check) when chuckd exits nonzero
- [ ] Informative log output (chuckd's default TEXT output is sufficient)
- [ ] Git-compare mode via `base-ref` input (this is the primary UX for PR validation — without it, users must manually manage a "previous schema" file)

### Add After Validation (v1.x)

Features to add once the core action is working and adopted.

- [ ] Multi-value `previous-schema` (newline-separated list) — when users report needing transitive checks against multiple pinned versions
- [ ] `output-format: json` input — when users report needing structured log output for their log aggregation pipelines
- [ ] `fail-on-incompatibility` boolean input (default: true) — when users request a "report but don't fail" mode for initial adoption/audit workflows

### Future Consideration (v2+)

Features to defer until there is clear demand.

- [ ] Binary checksum verification — when security-policy teams require it (needs publishing checksum files alongside releases)
- [ ] Binary caching via `actions/cache` — when action invocation time is measured and shown to be a bottleneck
- [ ] Windows support — requires new build matrix in build-release.yml, significant scope

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Core inputs (paths, format, compatibility) | HIGH | LOW | P1 |
| Platform detection + binary download | HIGH | MEDIUM | P1 |
| Fail on incompatibility (exit code) | HIGH | LOW | P1 |
| Log output (step log) | HIGH | LOW | P1 |
| `version` input with default | HIGH | LOW | P1 |
| Action metadata (name, branding) | MEDIUM | LOW | P1 |
| Git-compare mode (`base-ref`) | HIGH | MEDIUM | P1 |
| Unsupported platform error message | MEDIUM | LOW | P1 |
| Multi-value `previous-schema` | MEDIUM | MEDIUM | P2 |
| `output-format: json` | LOW | LOW | P2 |
| `fail-on-incompatibility` bypass input | LOW | LOW | P2 |
| Checksum verification | MEDIUM | HIGH | P3 |
| Binary caching | LOW | MEDIUM | P3 |
| PR comment posting | MEDIUM | HIGH | Anti-feature (v1) |
| Auto-detect changed schemas | MEDIUM | HIGH | Anti-feature (v1) |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

---

## Competitor Feature Analysis

| Feature | buf-action | cosign-installer | trivy-action | chuckd action (planned) |
|---------|------------|------------------|--------------|------------------------|
| Binary download from releases | Yes (via Node.js installer) | Yes (composite, curl) | Yes (via setup sub-action) | Yes (composite, curl) |
| Platform detection | Yes (Node.js) | Yes (shell case) | Yes (via setup sub-action) | Yes (shell case) |
| Version pinning input | Yes | Yes | Yes | Yes |
| Git-aware base comparison | Yes (`breaking_against` defaults to PR base) | N/A | N/A | Yes (`base-ref` input, git show) |
| PR comment posting | Yes (optional, complex) | N/A | N/A | No (explicit anti-feature for v1) |
| `paths` multi-value input | Yes | N/A | N/A | Yes (multi-value `previous-schema`) |
| Branding in marketplace | Yes | Yes | Yes | Yes |
| Checksum verification | Yes (optional) | Yes (required) | No | No (v1), deferred |
| Multiple schema formats | Protobuf only | N/A | N/A | JSON Schema, Avro, Protobuf |

**Key insight from competitor analysis:** buf-action is the closest comparable (breaking
change detection for schema files in PRs). Its architecture is more complex (Node.js
action with a built-in binary installer) because buf has more features (lint, format,
push). chuckd's action can be significantly simpler — a pure composite shell action —
because it does exactly one thing: check compatibility between two schema versions.
Simplicity is a feature.

---

## Sources

- Direct inspection of `bufbuild/buf-action` action.yml (fetched live 2026-03-07) — HIGH confidence: inputs design, git-compare (`breaking_against`), PR comment pattern
- Direct inspection of `sigstore/cosign-installer` action.yml (fetched live 2026-03-07) — HIGH confidence: composite action pattern, shell-based platform detection, `install-dir`/`$HOME` placement pattern
- Direct inspection of `aquasecurity/trivy-action` action.yaml (fetched live 2026-03-07) — HIGH confidence: inputs design, skip flags, version input
- Direct inspection of `instrumenta/conftest-action` action.yml (fetched live 2026-03-07) — HIGH confidence: `files` input, Docker-based action (negative example for this project)
- Direct inspection of `reviewdog/action-golangci-lint` action.yml (fetched live 2026-03-07) — HIGH confidence: multi-input patterns, version inputs
- Direct inspection of `chuckd/app/src/main/java/com/anentropic/chuckd/ChuckD.java` — HIGH confidence: all CLI flags, defaults, exit code semantics
- `.planning/PROJECT.md` — HIGH confidence: scope decisions, out-of-scope list, constraints
- `.planning/research/ARCHITECTURE.md` — HIGH confidence: platform detection mapping, data flow, anti-patterns
- `.planning/research/STACK.md` — HIGH confidence: composite action type decision, tool choices

---
*Feature research for: GitHub Actions composite action wrapping chuckd CLI*
*Researched: 2026-03-07*
