# Phase 2: Action Foundation - Context

**Gathered:** 2026-03-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Create a composite GitHub Action in `anentropic/chuckd-action` with marketplace metadata, input declarations, platform detection, and binary download with caching. Linux runners only. The action downloads the correct chuckd native binary and makes it available for subsequent steps.

</domain>

<decisions>
## Implementation Decisions

### Platform support
- Linux x86_64 and Linux aarch64 only — macOS dropped entirely (not needed for CI schema validation)
- Unsupported platforms (macOS, Windows) fail with error: platform name + suggestion to use Docker image as alternative
- Platform mapping: GHA `X64`/`ARM64` → artifact names `x86_64`/`aarch64`

### Version resolution (A+D strategy)
- Action stores a hardcoded compatible range (e.g. `CHUCKD_COMPAT=1.0`)
- No version input → query GitHub API for latest release matching `1.0.*`, download that
- `version: 1.0.2` input → validate against compatible range, download exact version
- Incompatible version input → fail with "chuckd X.Y.Z is not compatible with this action version. Requires 1.0.x."
- major.minor versions synced between chuckd and chuckd-action: new chuckd minor = new action minor, patch versions independent
- GitHub API called with GITHUB_TOKEN (available in workflow context, no rate limit concern)

### Binary download and caching
- Hybrid action: composite action.yml + small JS script for download/caching
- JS script uses `@actions/tool-cache` (purpose-built for this pattern — what setup-node, setup-go use)
- Also uses `@actions/core` for inputs/outputs/logging
- Plain JavaScript, not TypeScript
- Dependencies bundled with `ncc` into single `dist/index.js` — no committed node_modules
- Binary placed in tool-cache, added to PATH

### Repo bootstrapping
- MIT license (simpler than Apache-2.0, more common for GitHub Actions)
- Full README: usage examples, input reference, supported platforms, version compatibility
- No CI workflow in Phase 2 — deferred to Phase 4 alongside integration tests
- .gitignore for node_modules, standard Node patterns

### Claude's Discretion
- action.yml branding (icon, color)
- Exact JS script structure and error handling
- README structure and formatting
- .gitignore contents
- package.json setup

</decisions>

<specifics>
## Specific Ideas

- Artifact naming convention from chuckd releases: `chuckd-{Linux-x86_64,Linux-aarch64}-{version}.tar.gz`
- The `ncc` bundling pattern follows GitHub's own actions (actions/checkout commits dist/)
- Version coupling policy: action doesn't need to support arbitrary old chuckd versions — compatible range prevents backwards compat burden

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- chuckd release workflow (`build-release.yml`): defines artifact naming convention and platform matrix
- Existing `actions/checkout@v6` usage: confirms current GHA action versions

### Established Patterns
- Release artifacts: `chuckd-{platform-name}-{version}.tar.gz` where platform-name is `macOS-aarch64`, `Linux-x86_64`, `Linux-aarch64`
- Release process: tag-triggered, draft → publish flow via softprops/action-gh-release
- GitHub releases at `github.com/anentropic/chuckd/releases`

### Integration Points
- GitHub Releases API: source for binary downloads and version resolution
- chuckd CLI interface (from Phase 1): exit codes 0/1/2/3, arg order `<previous...> <new>`, `--quiet`, `--output`, `--format`, `--compatibility`, `--log-level`

</code_context>

<deferred>
## Deferred Ideas

- macOS aarch64 platform support — dropped from scope (not needed for CI use case)
- CI workflow for action repo — Phase 4
- Integration tests — Phase 4 (REL-01, REL-02)
- Update Phase 4 REL-01: remove macOS aarch64 runner requirement

</deferred>

---

*Phase: 02-action-foundation*
*Context gathered: 2026-03-08*
