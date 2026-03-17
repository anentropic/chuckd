# Phase 4: Git Compare and Release - Research

**Researched:** 2026-03-17
**Domain:** GitHub Actions git integration, CI/CD release workflows, integration testing patterns
**Confidence:** HIGH

## Summary

Phase 4 completes the chuckd-action by implementing the git-compare mode (extracting the previous schema from a base branch via `git show`), adding shallow-clone detection, building an integration test workflow in a separate repo (`anentropic/chuckd-example`), and establishing a floating v1 tag release process.

The git-compare implementation is straightforward: `git show <base-ref>:<schema-file>` emits the file content to stdout, which gets written to a temp file in `$RUNNER_TEMP` and passed to chuckd as the "previous schema". Shallow clone detection uses `git rev-parse --is-shallow-repository` which outputs the literal string `"true"` or `"false"`. Both git commands run via Node.js `execFile` (no shell spawn needed).

The integration test in `anentropic/chuckd-example` uses `continue-on-error: true` plus a follow-up assertion step checking `steps.step.outcome` to test expected-failure scenarios. The release workflow force-updates the floating `v1` tag whenever a new release is published.

**Primary recommendation:** Implement git-compare as a two-step sequence in `runValidation()`: detect shallow clone first (fail early with clear message), then `git show` the base-ref file to `$RUNNER_TEMP`, then invoke chuckd with that temp path as the previous schema. Keep all three steps inside the same `core.startGroup`.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| GIT-01 | User specifies base-ref input; action extracts previous schema from that branch using git show | `git show <ref>:<path>` command, execFile wrapper, temp file via `$RUNNER_TEMP` |
| GIT-02 | Action extracts previous schema version from base branch using git show | Same as above — git show emits file to stdout, write to temp file |
| GIT-03 | Action detects shallow clone and fails with clear message advising fetch-depth: 0 | `git rev-parse --is-shallow-repository` outputs `"true"` / `"false"` |
| REL-01 | Integration test workflow validates action on Linux x86_64, Linux aarch64, macOS aarch64 | Runner labels: `ubuntu-24.04`, `ubuntu-24.04-arm`, `macos-15` |
| REL-02 | Integration test covers both explicit-path mode and git-compare mode scenarios | `continue-on-error` + `steps.X.outcome` assertion pattern |
| REL-03 | Release process creates/maintains floating v1 tag | `git tag -fa v1` + `git push origin v1 --force` on release event |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `node:child_process` (execFile) | built-in | Run `git show` and `git rev-parse` | Already used for chuckd spawn; execFile is preferred for non-shell commands — no shell injection surface |
| `node:fs/promises` (writeFile) | built-in | Write `git show` stdout to temp file | Already in stdlib, no dependency needed |
| `node:os` (tmpdir) | built-in | Fallback temp dir if RUNNER_TEMP unset | Standard for cross-platform temp paths |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@actions/core` | 3.0.0 (installed) | setFailed, info, startGroup | Already in use — no change needed |
| `actions/checkout@v4` | v4 | Checkout in integration test repo | Current stable; v6 exists but v4 is still supported |

**Note on @actions/core version:** The installed version is 3.0.0. The STATE.md records a past decision to pin to `^1.11.1` because "ESM-only exports in 3.0.0 break ncc 0.38.4 CJS bundling". However, this project uses rollup (not ncc), so 3.0.0 is already installed and working. No change needed.

### No new npm dependencies needed
The git-compare implementation uses only Node.js builtins (`child_process`, `fs/promises`, `os`, `path`). No new packages required.

**Version verification:**
- `@actions/core`: 3.0.0 (npm registry confirmed 2026-03-17)
- `@actions/tool-cache`: 4.0.0 (npm registry confirmed 2026-03-17)

## Architecture Patterns

### Recommended Project Structure (chuckd-action additions)

No new files needed. All changes land in existing files:

```
chuckd-action/
├── src/
│   └── validate.js       # Add extractBaseRefSchema(), update runValidation()
├── tests/
│   └── validate.test.js  # Add tests for git-compare flow
├── dist/
│   └── index.js          # Rebuilt bundle
└── .github/
    └── workflows/
        └── release.yml   # New: floating v1 tag workflow (triggered on release)

chuckd-example/ (separate repo, new)
├── schemas/
│   ├── person.v1.json    # Compatible previous schema
│   ├── person.v2.json    # Compatible new schema (for passing test)
│   └── person.v3.json    # Incompatible schema (for expected-failure test)
└── .github/
    └── workflows/
        └── integration-test.yml  # Matrix across 3 runners, tests both modes
```

### Pattern 1: Git Show with execFile

**What:** Use `execFile` (not `spawn`) to run `git show <ref>:<path>` and capture stdout as a string, then write to `$RUNNER_TEMP/<filename>`.

**When to use:** Any time you need to read a file from another branch without checking it out.

**Why execFile over spawn:** No shell is spawned, so there's no shell injection risk from user-supplied ref or path values. The command must be an executable, not a shell builtin.

```javascript
// Source: Node.js docs (node:child_process execFile)
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';

const execFileAsync = promisify(execFile);

async function extractBaseRefSchema(baseRef, schemaFile) {
  const gitRef = `${baseRef}:${schemaFile}`;
  const { stdout } = await execFileAsync('git', ['show', gitRef]);
  // Write to RUNNER_TEMP so chuckd can read it as a normal file path
  const tmpDir = process.env.RUNNER_TEMP || os.tmpdir();
  const tmpPath = path.join(tmpDir, 'chuckd-base-schema-' + Date.now() + '.json');
  await writeFile(tmpPath, stdout);
  return tmpPath;
}
```

**Error handling:** If `git show` fails (ref doesn't exist, file not tracked at that ref), `execFile` rejects with a non-zero exit code. Catch and call `core.setFailed` with a descriptive message including the ref and path.

### Pattern 2: Shallow Clone Detection

**What:** Run `git rev-parse --is-shallow-repository` before attempting `git show`. Output is the literal string `"true\n"` or `"false\n"`.

**When to use:** At the start of git-compare mode, before any git show attempt.

```javascript
// Source: git-scm.com docs, git rev-parse --is-shallow-repository
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

async function isShallowClone() {
  try {
    const { stdout } = await execFileAsync('git', ['rev-parse', '--is-shallow-repository']);
    return stdout.trim() === 'true';
  } catch {
    // If rev-parse fails, we can't determine — assume not shallow
    return false;
  }
}
```

**Error message to user when shallow:**
```
Shallow clone detected. Set fetch-depth: 0 in your actions/checkout step to enable git-compare mode.
Example:
  - uses: actions/checkout@v4
    with:
      fetch-depth: 0
```

### Pattern 3: Integration Test — Expected Failure Verification

**What:** Use `continue-on-error: true` on a step that is expected to fail, then assert the outcome in a follow-up step.

**When to use:** Testing that the action correctly fails when schemas are incompatible, or when shallow clone is detected.

```yaml
# Source: GitHub Actions docs (steps context)
- name: Run incompatible schema check (should fail)
  id: incompatible-check
  uses: anentropic/chuckd-action@<ref>
  continue-on-error: true
  with:
    schema-file: schemas/person.v3.json
    previous-schemas: schemas/person.v1.json

- name: Assert step failed as expected
  if: steps.incompatible-check.outcome != 'failure'
  run: |
    echo "Expected incompatible check to fail, but it succeeded"
    exit 1
```

**Critical detail:** `steps.<id>.outcome` is `'failure'` when the step failed (even with `continue-on-error: true`). `steps.<id>.conclusion` would be `'success'` because `continue-on-error` suppresses the job failure. Always use `.outcome`, not `.conclusion`.

### Pattern 4: Floating v1 Tag Release Workflow

**What:** On every GitHub release creation, extract the major version and force-move the floating tag.

**When to use:** Release workflow in chuckd-action repo.

```yaml
# Source: actions/toolkit docs/action-versioning.md pattern
name: Update floating v1 tag

on:
  release:
    types: [published]

jobs:
  update-major-tag:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Move major version tag
        run: |
          VERSION=${GITHUB_REF#refs/tags/}
          MAJOR=${VERSION%%.*}
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git tag -fa "${MAJOR}" -m "Update ${MAJOR} tag to ${VERSION}"
          git push origin "${MAJOR}" --force
```

**Authentication:** The default `GITHUB_TOKEN` can push tags to the same repo. No PAT needed for this operation.

### Pattern 5: Integration Test Matrix (3 runners)

```yaml
# Source: GitHub Actions docs + runner labels verified 2026-03-17
jobs:
  integration-test:
    strategy:
      matrix:
        runner:
          - ubuntu-24.04         # Linux x86_64
          - ubuntu-24.04-arm     # Linux aarch64
          - macos-15             # macOS aarch64 (M-series)
    runs-on: ${{ matrix.runner }}
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # Required for git-compare mode tests
      - name: Test explicit-path mode (compatible)
        uses: anentropic/chuckd-action@<ref>
        with:
          schema-file: schemas/person.v2.json
          previous-schemas: schemas/person.v1.json
      # ... etc
```

### Anti-Patterns to Avoid

- **Using `spawn('git', ...)` for git show:** Works but requires manual stdout accumulation. Use `execFile` + `promisify` for simpler one-shot reads.
- **Writing temp file to workspace directory:** Use `$RUNNER_TEMP` — it's guaranteed writable and cleaned up automatically. Workspace writes can conflict with git status.
- **Using `steps.<id>.conclusion` to check expected failures:** Returns `'success'` when `continue-on-error: true`. Must use `.outcome`.
- **Pointing integration test at main branch during development:** Use a branch ref or commit SHA until ready to publish. The chuckd-example repo workflow uses `uses: anentropic/chuckd-action@<dev-branch>` during development.
- **Omitting `fetch-depth: 0` in integration test checkout:** The git-compare test scenario requires full history. Without it, `git show main:schemas/person.v1.json` will fail if that commit isn't in the shallow history.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Temp file management | Custom temp dir logic | `process.env.RUNNER_TEMP` + `node:path` | RUNNER_TEMP is guaranteed on all GHA runners; auto-cleaned |
| Git shallow detection | Checking `.git/shallow` file existence | `git rev-parse --is-shallow-repository` | Official git command; works across all git versions >= 2.15 |
| Major version tag maintenance | Manual tag management | `git tag -fa v1 && git push --force` in release workflow | Simple, standard pattern; no extra action needed |
| Testing expected failures | Complex failure-capture logic | `continue-on-error: true` + `.outcome` check | Built-in GHA primitives are sufficient |

**Key insight:** The git-compare feature is two shell commands (`git rev-parse` + `git show`) and a file write. No libraries needed. Resist adding dependencies for this.

## Common Pitfalls

### Pitfall 1: Shallow Clone Causes Silent Wrong Behavior
**What goes wrong:** If `git show main:schemas/person.json` is run on a shallow clone, git may fail with "fatal: no such path 'schemas/person.json' in 'main'" even if the file exists on main — because main's commits aren't in the shallow history.
**Why it happens:** `actions/checkout` defaults to `fetch-depth: 1` (single commit). The base branch ref isn't fetched.
**How to avoid:** Check `--is-shallow-repository` first. Fail immediately with a clear message pointing to `fetch-depth: 0`.
**Warning signs:** `execFile('git', ['show', ...])` rejects with "fatal: not a tree object" or "fatal: no such path".

### Pitfall 2: RUNNER_TEMP Not Set in Local Tests
**What goes wrong:** Unit tests for `extractBaseRefSchema` crash because `process.env.RUNNER_TEMP` is undefined.
**Why it happens:** RUNNER_TEMP is a GitHub Actions runner environment variable; it doesn't exist locally.
**How to avoid:** Fall back to `os.tmpdir()` when RUNNER_TEMP is not set. Mock the env var in unit tests.
**Warning signs:** `TypeError: Cannot read property ... of undefined` or path join errors in tests.

### Pitfall 3: `git show` Output Includes Trailing Newline
**What goes wrong:** Writing raw stdout from `git show` to a temp file may add extra bytes if the schema file doesn't end with a newline, or the output is slightly different from the working tree version.
**Why it happens:** `git show` outputs the file content verbatim — this is actually correct behavior. The content should match what's committed.
**How to avoid:** Write stdout directly to temp file without trimming. JSON parsers handle trailing newlines.

### Pitfall 4: Force-Push of v1 Tag Requires Specific Permissions
**What goes wrong:** Release workflow fails with "refusing to allow a GitHub App to create or update workflow" or similar permission error when pushing the floating tag.
**Why it happens:** The default GITHUB_TOKEN can push tags but the workflow file may need `contents: write` permission explicitly declared.
**How to avoid:** Add `permissions: contents: write` to the release workflow job, or at the top of the workflow file.

### Pitfall 5: `continue-on-error` Masks Unexpected Failures
**What goes wrong:** If the action fails for the wrong reason (e.g., binary download fails), `continue-on-error: true` hides it. The `.outcome` check only asserts "it failed", not "it failed for the right reason".
**Why it happens:** Integration tests can't easily inspect why a step failed.
**How to avoid:** Use descriptive step names and check the `exit-code` output when available: `steps.check.outputs.exit-code`. For incompatibility failures, assert `exit-code == '1'`; for shallow clone, it will be absent (setFailed before spawn).

### Pitfall 6: macOS Gatekeeper Quarantine on Downloaded Binaries
**What goes wrong:** On macOS runners, the chuckd binary downloaded via curl may be quarantined by Gatekeeper and refused execution.
**Why it happens:** Gatekeeper applies the `com.apple.quarantine` extended attribute to files downloaded by applications that opt into File Quarantine. However, `curl` itself does NOT set this attribute.
**How to avoid:** The existing binary download uses `@actions/tool-cache` which uses curl under the hood. In practice, files downloaded by curl in a GitHub Actions context do not get the quarantine attribute. This is LOW risk on GHA runners. If issues arise, `xattr -d com.apple.quarantine <binary>` resolves it.

## Code Examples

Verified patterns from official sources:

### Full git-compare implementation (runValidation update)
```javascript
// Pattern: shallow check → git show → temp file → chuckd invocation
// All within existing startGroup/endGroup try/finally pattern

async function extractBaseRefSchema(baseRef, schemaFile) {
  const execFileAsync = promisify(execFile);

  // Step 1: Detect shallow clone
  const { stdout: shallowOut } = await execFileAsync('git', ['rev-parse', '--is-shallow-repository']);
  if (shallowOut.trim() === 'true') {
    throw new Error(
      'Shallow clone detected. Set fetch-depth: 0 in your actions/checkout step:\n' +
      '  - uses: actions/checkout@v4\n' +
      '    with:\n' +
      '      fetch-depth: 0'
    );
  }

  // Step 2: Extract file content from base ref
  const gitRef = `${baseRef}:${schemaFile}`;
  const { stdout: fileContent } = await execFileAsync('git', ['show', gitRef]);

  // Step 3: Write to temp file
  const tmpDir = process.env.RUNNER_TEMP || os.tmpdir();
  const basename = path.basename(schemaFile, path.extname(schemaFile));
  const tmpPath = path.join(tmpDir, `chuckd-base-${basename}-${Date.now()}${path.extname(schemaFile)}`);
  await writeFile(tmpPath, fileContent);
  return tmpPath;
}
```

### release.yml for floating v1 tag
```yaml
name: Release

on:
  release:
    types: [published]

permissions:
  contents: write

jobs:
  update-major-tag:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Move major version tag
        run: |
          VERSION=${GITHUB_REF#refs/tags/}
          MAJOR=${VERSION%%.*}
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git tag -fa "${MAJOR}" -m "Update ${MAJOR} to ${VERSION}"
          git push origin "${MAJOR}" --force
```

### Integration test workflow structure (chuckd-example repo)
```yaml
name: Integration Tests

on:
  push:
  pull_request:
  workflow_dispatch:

jobs:
  test:
    strategy:
      fail-fast: false
      matrix:
        runner: [ubuntu-24.04, ubuntu-24.04-arm, macos-15]
    runs-on: ${{ matrix.runner }}

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # Test 1: Explicit-path mode, compatible (should succeed)
      - name: explicit-path compatible
        uses: anentropic/chuckd-action@<dev-branch-or-sha>
        with:
          schema-file: schemas/person.v2.json
          previous-schemas: schemas/person.v1.json

      # Test 2: Explicit-path mode, incompatible (should fail)
      - name: explicit-path incompatible
        id: explicit-incompatible
        uses: anentropic/chuckd-action@<dev-branch-or-sha>
        continue-on-error: true
        with:
          schema-file: schemas/person.v3.json
          previous-schemas: schemas/person.v1.json
      - name: assert explicit-incompatible failed
        if: steps.explicit-incompatible.outcome != 'failure'
        run: echo "Expected failure, got success" && exit 1

      # Test 3: git-compare mode, compatible (should succeed)
      - name: git-compare compatible
        uses: anentropic/chuckd-action@<dev-branch-or-sha>
        with:
          schema-file: schemas/person.v2.json
          base-ref: main

      # Test 4: git-compare mode, incompatible (should fail)
      - name: git-compare incompatible
        id: git-compare-incompatible
        uses: anentropic/chuckd-action@<dev-branch-or-sha>
        continue-on-error: true
        with:
          schema-file: schemas/person.v3.json
          base-ref: main
      - name: assert git-compare-incompatible failed
        if: steps.git-compare-incompatible.outcome != 'failure'
        run: echo "Expected failure, got success" && exit 1
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Docker-based actions | Composite/node actions with native binaries | ~2021 | Faster startup, simpler distribution |
| Manual floating tag management | Release workflow auto-updates v1 | ~2022 | Zero-maintenance versioning |
| Separate Linux aarch64 runners required larger runner tier | `ubuntu-24.04-arm` free for public repos | 2025-01 (public preview), 2025-08 (GA) | No cost barrier for 3-platform matrix |
| Testing actions required same repo | `uses: owner/repo@ref` from any repo | Always supported | Integration tests can live in separate demo repo |

**Deprecated/outdated:**
- `macos-13` (Intel): Being retired by GitHub; use `macos-14` or `macos-15` for ARM64
- `ubuntu-22.04-arm`: Still available but `ubuntu-24.04-arm` is current; fine to use either

## Open Questions

1. **chuckd-example repo fixture schema design**
   - What we know: Need at least one compatible pair and one incompatible pair of schemas; one for explicit-path tests, one for git-compare tests
   - What's unclear: Whether the same schema files can serve both modes (they can — git-compare reads from a ref, explicit-path reads from disk paths)
   - Recommendation: Use two schema files: `person.v1.json` (base) and `person.v2.json` (compatible new). Add `person.v2-breaking.json` (incompatible). Commit all to main so `git show main:schemas/...` works.

2. **git-compare mode in pull_request event context**
   - What we know: In a PR, `github.base_ref` is the target branch (e.g., `main`). The user passes this as `base-ref`. `git show main:<path>` works because `fetch-depth: 0` fetches all branches.
   - What's unclear: Whether `git show origin/main:<path>` is needed vs `git show main:<path>` when the remote tracking branch is checked out
   - Recommendation: Document that users should pass `base-ref: ${{ github.base_ref }}` and use `fetch-depth: 0`. Both `main` and `origin/main` should resolve in a full fetch. Test both forms in integration test.

3. **buildArgs for git-compare mode**
   - What we know: `buildArgs()` currently returns nothing for git-compare mode (falls through). After `extractBaseRefSchema()` returns a temp file path, `buildArgs()` needs to treat git-compare like explicit mode with a single previous file.
   - What's unclear: Whether to modify `buildArgs()` signature or handle git-compare in `runValidation()` directly before calling `buildArgs()`.
   - Recommendation: Resolve the temp path in `runValidation()` before calling `buildArgs()`, then call `buildArgs('explicit', { ...inputs, previousSchemas: tmpPath })`. This avoids mutating `buildArgs()` signature and keeps temp file logic in one place.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Jest 29.7.0 |
| Config file | `/Users/paul/Documents/Dev/Personal/chuckd-action/jest.config.js` |
| Quick run command | `node --experimental-vm-modules node_modules/.bin/jest --passWithNoTests` |
| Full suite command | `node --experimental-vm-modules node_modules/.bin/jest --passWithNoTests` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GIT-01 | `runValidation()` in git-compare mode extracts schema from base-ref | unit | `jest tests/validate.test.js` | ✅ (add to existing) |
| GIT-02 | `extractBaseRefSchema()` calls git show and writes temp file | unit | `jest tests/validate.test.js` | ✅ (add to existing) |
| GIT-03 | Shallow clone detected before git show; setFailed with fetch-depth message | unit | `jest tests/validate.test.js` | ✅ (add to existing) |
| REL-01 | Action runs on 3 runners (Linux x86, ARM, macOS arm) | integration | workflow in chuckd-example repo | ❌ Wave 0 |
| REL-02 | Explicit-path and git-compare scenarios both covered | integration | workflow in chuckd-example repo | ❌ Wave 0 |
| REL-03 | Release workflow updates v1 tag | manual / workflow | `.github/workflows/release.yml` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `npm test` in chuckd-action repo (39 existing + new git-compare tests)
- **Per wave merge:** `npm test` full suite
- **Phase gate:** Full Jest suite green + integration test workflow passing in chuckd-example repo before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `chuckd-example` repo must exist with fixture schemas and integration test workflow (REL-01, REL-02)
- [ ] `.github/workflows/release.yml` in chuckd-action repo (REL-03)
- [ ] New test cases in `tests/validate.test.js` for `extractBaseRefSchema()` and shallow clone detection (GIT-01, GIT-02, GIT-03) — these tests can be added incrementally during implementation

## Sources

### Primary (HIGH confidence)
- Node.js docs (`node:child_process` execFile) — execFile API, promisify pattern
- Git official docs (git-scm.com) — `git show <ref>:<path>` syntax, `git rev-parse --is-shallow-repository`
- GitHub Actions docs (docs.github.com/en/actions/reference/runners) — runner labels verified 2026-03-17
- actions/checkout GitHub README — fetch-depth behavior, v6 current version

### Secondary (MEDIUM confidence)
- GitHub Actions docs (steps context, continue-on-error, .outcome vs .conclusion) — verified against official GHA docs
- actions/toolkit docs (action-versioning.md) — floating major version tag pattern
- GitHub Changelog 2025-01-16 — Linux arm64 free for public repos, GA August 2025

### Tertiary (LOW confidence)
- macOS Gatekeeper + curl quarantine behavior — confirmed by multiple security sources that curl does not set quarantine attribute; LOW because not officially documented by GitHub for GHA runners specifically

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all Node.js builtins, no new npm packages; existing test infra unchanged
- Architecture: HIGH — git show + execFile is the direct, obvious implementation; release workflow pattern is from official actions/toolkit docs
- Pitfalls: MEDIUM-HIGH — shallow clone and RUNNER_TEMP pitfalls verified; Gatekeeper pitfall marked LOW confidence

**Research date:** 2026-03-17
**Valid until:** 2026-06-17 (stable domain; runner labels and release patterns change slowly)
