# Phase 3: Core Validation - Context

**Gathered:** 2026-03-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire action inputs to chuckd CLI and execute validation in explicit-path mode and glob mode. Map format, compatibility, and log-level inputs to CLI flags. Fail the workflow step on incompatibility with details in the GitHub Actions log. Git-compare mode (base-ref) is Phase 4.

</domain>

<decisions>
## Implementation Decisions

### Input restructuring
- Replace current `schema-file` (required) + `previous-schemas` (optional) with a new primary input: `schema-pattern` (glob pattern, e.g. `schemas/person.*`)
- Glob mode is the expected default usage pattern; explicit paths are opt-in
- All schema inputs optional in action.yml — action validates exactly one mode at runtime:
  1. `schema-pattern` set → glob mode (pass pattern as sole arg to chuckd)
  2. `schema-file` + `previous-schemas` set → explicit-path mode
  3. `schema-file` + `base-ref` set → git-compare mode (Phase 4)
- If none of the above are satisfied, fail with clear guidance: "Either schema-pattern, or schema-file with previous-schemas/base-ref, must be specified"
- `previous-schemas` supports both space-separated and newline-separated paths (split on whitespace) for YAML multiline convenience

### Glob expansion
- Action passes the quoted pattern straight to chuckd — chuckd handles glob expansion with natural sort (Phase 1 logic)
- No duplication of glob/sort logic in the action

### Log presentation
- Wrap chuckd output in a collapsible `::group::` block in GHA logs
- Add `::error::` annotation for the overall failure with a summary message
- Show chuckd stderr metadata (no `--quiet`) — useful for debugging which files are being compared
- Add an `output-format` input (default: `TEXT`) letting users choose TEXT or JSON for chuckd's `--output` flag

### Error annotation format
- Incompatibility (exit 1): `::error::` with summary including schema names, e.g. "Schema incompatibility detected: person-v2.json is not BACKWARD compatible"
- Usage error (exit 2): `::error::` with "Invalid arguments — check action inputs" + chuckd's error output
- Runtime error (exit 3): `::error::` with "Runtime error — file not found or schema parse failure" + chuckd's error output
- Unexpected exit code: `::error::` with "Unexpected exit code N" — defensive for future chuckd versions

### Action outputs
- Set `exit-code` output variable with chuckd's exit code (0/1/2/3) so downstream steps can branch on it
- No `compatible` boolean output — `exit-code` is sufficient

### Input validation strategy
- Validate mode selection upfront (exactly one mode specified)
- Do NOT validate format/compatibility enum values — let chuckd reject bad values (exit 2), action reports as usage error
- Do NOT check file existence — let chuckd handle missing files (exit 3), action reports as runtime error

### Claude's Discretion
- Exact `::group::` label text
- How to construct the chuckd command (child_process.exec vs spawn)
- Whether to add the new `schema-pattern` and `output-format` inputs to action.yml in this phase or restructure in a separate plan
- Test structure and mocking approach

</decisions>

<specifics>
## Specific Ideas

- Glob mode as primary reflects how users naturally version schemas (person.v1.json, person.v2.json in a directory) — the action should make the common case easy
- The three-mode input validation (glob / explicit / git-compare) should give clear error messages identifying which mode the action detected vs what's missing

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/index.js`: Current entry point — handles platform detection, version resolution, binary download/cache, adds to PATH. Validation logic extends this.
- `src/platform.js`: Platform detection (Linux x86_64/aarch64 only)
- `src/version.js`: Version resolution from GitHub API
- `@actions/core`: Already used for inputs, logging, setFailed — also has `setOutput()`, `startGroup()`/`endGroup()`, `error()`
- `@actions/tool-cache`: Already used for binary download/caching

### Established Patterns
- Plain JavaScript with ES modules (`import`/`export`)
- `ncc` bundling to `dist/index.js`
- Jest for testing
- `core.getInput()` for reading action inputs
- `core.setFailed()` for failing the step

### Integration Points
- `src/index.js` `run()` function: After binary is on PATH, add chuckd execution step
- `action.yml` inputs: Need to add `schema-pattern` and `output-format`, make `schema-file` optional
- chuckd CLI interface: `chuckd [--format F] [--compatibility C] [--log-level L] [--output O] <previous...> <new>` (explicit) or `chuckd [flags] <pattern>` (glob)
- Exit codes: 0=compatible, 1=incompatible, 2=usage error, 3=runtime error

</code_context>

<deferred>
## Deferred Ideas

- Structured JSON output as GHA output variable (OUT-02) — v2
- PR comment posting with incompatibility details (OUT-01) — v2
- Auto-detection of changed schema files (DET-01) — v2

</deferred>

---

*Phase: 03-core-validation*
*Context gathered: 2026-03-08*
