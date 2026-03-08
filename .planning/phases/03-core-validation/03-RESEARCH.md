# Phase 3: Core Validation - Research

**Researched:** 2026-03-08
**Domain:** GitHub Actions JavaScript action — Node.js child_process, @actions/core workflow commands, action.yml input restructuring
**Confidence:** HIGH

## Summary

Phase 3 wires the already-installed chuckd binary to the action's execution logic. The binary is on PATH by the end of Phase 2's `run()` function; this phase adds a validation step that (1) reads inputs and determines which mode is active, (2) builds and executes the chuckd command, (3) streams output into a `::group::` block, and (4) emits annotations and sets the `exit-code` output.

The codebase is well-positioned for this work. The `@actions/core` 3.0.0 installed in the project provides all the needed workflow command APIs: `startGroup`/`endGroup`, `error` (annotations), `setOutput`, and `setFailed`. The rollup-based ESM bundling pipeline handles the 3.0.0 package cleanly (unlike the discarded ncc+CJS approach noted in STATE.md).

The one gap to address in `action.yml` is a mismatch: the existing `format` input defaults to `JSON_SCHEMA` but the chuckd CLI enum value is `JSONSCHEMA`. The plan must add the new `schema-pattern` and `output-format` inputs, make `schema-file` optional, and fix this enum mismatch.

**Primary recommendation:** Add a new `src/validate.js` module for all validation logic, called from `src/index.js` after the binary is on PATH. Keep `child_process.spawn` with promise wrapping for streaming output. Unit-test `validate.js` in isolation by mocking `@actions/core` and `child_process`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Replace current `schema-file` (required) + `previous-schemas` (optional) with new primary input `schema-pattern` (glob pattern)
- All schema inputs optional in action.yml — action validates exactly one mode at runtime:
  1. `schema-pattern` set → glob mode (pass pattern as sole arg to chuckd)
  2. `schema-file` + `previous-schemas` set → explicit-path mode
  3. `schema-file` + `base-ref` set → git-compare mode (Phase 4)
  4. If none satisfied: fail with "Either schema-pattern, or schema-file with previous-schemas/base-ref, must be specified"
- `previous-schemas` supports both space-separated and newline-separated paths (split on whitespace)
- Action passes the quoted pattern straight to chuckd — chuckd handles glob expansion
- Wrap chuckd output in a collapsible `::group::` block in GHA logs
- Add `::error::` annotation for the overall failure with a summary message
- Show chuckd stderr metadata (no `--quiet`) — useful for debugging
- Add an `output-format` input (default: `TEXT`) letting users choose TEXT or JSON for chuckd's `--output` flag
- Error annotation format:
  - Exit 1 (incompatibility): `::error::` with "Schema incompatibility detected: ..." including schema name
  - Exit 2 (usage): `::error::` with "Invalid arguments — check action inputs"
  - Exit 3 (runtime): `::error::` with "Runtime error — file not found or schema parse failure"
  - Unexpected exit: `::error::` with "Unexpected exit code N"
- Set `exit-code` output variable with chuckd's exit code (0/1/2/3)
- No `compatible` boolean output
- Validate mode selection upfront (exactly one mode specified)
- Do NOT validate format/compatibility enum values — let chuckd reject bad values (exit 2)
- Do NOT check file existence — let chuckd handle missing files (exit 3)

### Claude's Discretion
- Exact `::group::` label text
- How to construct the chuckd command (child_process.exec vs spawn)
- Whether to add the new `schema-pattern` and `output-format` inputs to action.yml in this phase or restructure in a separate plan
- Test structure and mocking approach

### Deferred Ideas (OUT OF SCOPE)
- Structured JSON output as GHA output variable (OUT-02) — v2
- PR comment posting with incompatibility details (OUT-01) — v2
- Auto-detection of changed schema files (DET-01) — v2
- Git-compare mode (base-ref) — Phase 4
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| VAL-01 | User can specify schema file and one or more previous schema file paths for comparison | Explicit-path mode: build `[...previousPaths, schemaFile]` as CLI args after splitting `previous-schemas` on whitespace |
| VAL-02 | Action maps format, compatibility, and log-level inputs to chuckd CLI flags | CLI flags confirmed: `-f`/`--format`, `-c`/`--compatibility`, `-l`/`--log-level`, `-o`/`--output`; must fix JSON_SCHEMA → JSONSCHEMA enum mismatch |
| VAL-03 | Action outputs incompatibility details to GitHub Actions logs on failure | `core.startGroup`/`core.endGroup` for output grouping; `core.error()` for annotations; chuckd stdout/stderr streamed into the group block |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `@actions/core` | 3.0.0 (installed) | Workflow commands: inputs, outputs, annotations, groups, setFailed | Official GHA toolkit — already in use |
| `node:child_process` | built-in | Spawn chuckd subprocess, capture stdout/stderr, get exit code | Standard Node.js — no additional dependency |
| `node:path` | built-in | Path manipulation for schema file args | Standard Node.js |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jest | 29.7.0 (installed) | Unit testing with ESM support | Already configured; test validate.js in isolation |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `spawn` with promise wrapper | `exec` (buffered) | exec buffers all output before returning — fine for small output but spawn lets us stream to group in real-time; spawn is preferred |
| `spawn` with promise wrapper | `spawnSync` (blocking) | spawnSync blocks the Node.js event loop — avoid in async action code |

**No new installations required.** All dependencies are already present.

## Architecture Patterns

### Recommended Project Structure
```
src/
├── index.js          # Entry point: platform, version, download, PATH, then run()
├── validate.js       # NEW: mode detection, command building, chuckd execution, log/annotation
├── platform.js       # Existing: RUNNER_OS/ARCH → artifact platform name
└── version.js        # Existing: version resolution and validation
tests/
├── platform.test.js  # Existing
├── version.test.js   # Existing
└── validate.test.js  # NEW: unit tests for validate.js logic
```

### Pattern 1: Module Separation — `validate.js`
**What:** All validation logic in its own module, exported as `runValidation(inputs)`.
**When to use:** Keeps index.js focused on setup. Validate.js can be unit-tested in isolation by mocking `@actions/core` and `child_process`.
**Example:**
```javascript
// src/validate.js
import * as core from '@actions/core';
import { spawn } from 'node:child_process';

export function detectMode(inputs) {
  const { schemaPattern, schemaFile, previousSchemas, baseRef } = inputs;
  const hasPattern = !!schemaPattern;
  const hasExplicit = !!schemaFile && !!previousSchemas;
  const hasGitCompare = !!schemaFile && !!baseRef;

  if (hasPattern && !schemaFile && !baseRef) return 'glob';
  if (hasExplicit && !schemaPattern && !baseRef) return 'explicit';
  if (hasGitCompare && !schemaPattern && !previousSchemas) return 'git-compare';

  throw new Error(
    'Either schema-pattern, or schema-file with previous-schemas/base-ref, must be specified'
  );
}

export function buildArgs(mode, inputs) {
  const { schemaPattern, schemaFile, previousSchemas, format, compatibility, logLevel, outputFormat } = inputs;
  const args = [];

  // Flags
  if (format)       args.push('--format', format);
  if (compatibility) args.push('--compatibility', compatibility);
  if (logLevel)     args.push('--log-level', logLevel);
  if (outputFormat) args.push('--output', outputFormat);

  // Positional args (mode-specific)
  if (mode === 'glob') {
    args.push(schemaPattern);
  } else if (mode === 'explicit') {
    // Split on whitespace (handles both space-separated and newline-separated)
    const previous = previousSchemas.trim().split(/\s+/).filter(Boolean);
    args.push(...previous, schemaFile);
  }

  return args;
}
```

### Pattern 2: spawn with Promise Wrapper
**What:** Wrap `child_process.spawn` in a Promise that resolves with `{ exitCode, stdout, stderr }`.
**When to use:** Gives full control over exit code handling without the "non-zero exit rejects promise" behavior of `execFile`. Critical here because exit code 1 (incompatible) is NOT an error — we need to read the output.

**Example:**
```javascript
// Source: Node.js docs — child_process.spawn
function runChuckd(args) {
  return new Promise((resolve) => {
    const child = spawn('chuckd', args);
    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });

    child.on('close', (code) => {
      resolve({ exitCode: code, stdout, stderr });
    });

    child.on('error', (err) => {
      // Binary not on PATH or permission denied
      resolve({ exitCode: -1, stdout: '', stderr: err.message });
    });
  });
}
```

### Pattern 3: @actions/core Workflow Commands
**What:** Use the official toolkit methods for GHA log output.
**When to use:** All log output in this phase.
**Example:**
```javascript
// Source: @actions/core 3.0.0 type definitions (installed)
core.startGroup('chuckd validation output');
// ... log stdout/stderr lines ...
core.endGroup();

// Annotation — appears in Actions UI and on PR
core.error('Schema incompatibility detected: person-v2.json is not BACKWARD compatible');

// Output variable — accessible to downstream steps
core.setOutput('exit-code', String(exitCode));

// Fail the step
core.setFailed('Schema incompatibility detected');
```

### Pattern 4: Input Reading in validate.js
```javascript
// Read all inputs needed for validation
export function readInputs() {
  return {
    schemaPattern:   core.getInput('schema-pattern'),
    schemaFile:      core.getInput('schema-file'),
    previousSchemas: core.getInput('previous-schemas'),
    baseRef:         core.getInput('base-ref'),
    format:          core.getInput('format'),
    compatibility:   core.getInput('compatibility'),
    logLevel:        core.getInput('log-level'),
    outputFormat:    core.getInput('output-format'),
  };
}
```

### Anti-Patterns to Avoid
- **Using `exec` instead of `spawn`:** exec buffers all output and throws on non-zero exit. Exit code 1 is a normal chuckd result (incompatible), not an error. Use spawn with manual exit code handling.
- **Calling `core.setFailed` immediately on non-zero exit:** Always emit annotations and stream output into the group BEFORE calling setFailed, so logs appear in the grouped block.
- **Splitting `previous-schemas` on comma:** The decided separator is whitespace (handles both space-separated and newline-separated YAML). Use `.trim().split(/\s+/).filter(Boolean)`.
- **Passing `JSON_SCHEMA` as the format flag to chuckd:** The chuckd CLI enum is `JSONSCHEMA` (no underscore). The existing action.yml default is wrong and must be fixed.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Collapsible log groups | Custom ::group:: echo | `core.startGroup()` / `core.endGroup()` | Official API handles the workflow command syntax correctly |
| Error annotations | Custom ::error:: echo | `core.error(message, properties)` | Handles escaping of special characters in message |
| Action output variables | Custom GITHUB_OUTPUT writes | `core.setOutput(name, value)` | Official API handles the file-append protocol correctly |
| Failing the action step | `process.exit(1)` | `core.setFailed(message)` | setFailed sets the exit code AND emits a final error log line |

**Key insight:** The `@actions/core` toolkit wraps workflow command syntax (`::command::`) that contains quoting and escaping rules. Hand-rolling the echo strings is fragile and version-sensitive.

## Common Pitfalls

### Pitfall 1: Exit Code 1 Treated as Runtime Failure
**What goes wrong:** Using `exec` or `execFile` without catching the rejection — exit 1 from chuckd (incompatible schemas) throws, and the catch block may misclassify it as a runtime error.
**Why it happens:** `exec`/`execFile` reject their promise on non-zero exit codes.
**How to avoid:** Use `spawn` with a manual `close` event handler. Resolve (not reject) with the exit code regardless of value.
**Warning signs:** "Command failed: chuckd..." in error messages when you expected incompatibility output.

### Pitfall 2: Format Enum Mismatch
**What goes wrong:** `action.yml` currently has `default: 'JSON_SCHEMA'` for the `format` input. The chuckd CLI enum is `JSONSCHEMA`. Passing `JSON_SCHEMA` to chuckd's `-f` flag causes a usage error (exit 2).
**Why it happens:** Initial action.yml scaffold used a different naming convention.
**How to avoid:** Fix `action.yml` format input default to `JSONSCHEMA`. Update description to list `JSONSCHEMA, AVRO, PROTOBUF`. This is a required fix in this phase.
**Warning signs:** All runs fail with exit 2 even with valid schema files.

### Pitfall 3: Group Not Closed on Error
**What goes wrong:** If an exception is thrown between `core.startGroup()` and `core.endGroup()`, the group is never closed. Subsequent log output appears nested in the group.
**Why it happens:** Early return or throw before endGroup.
**How to avoid:** Use try/finally to guarantee `core.endGroup()` is always called.
**Warning signs:** Log output after the chuckd step appears indented/grouped unexpectedly.

### Pitfall 4: Empty `previous-schemas` in Explicit Mode
**What goes wrong:** User sets `schema-file` but omits `previous-schemas` (or leaves it empty string). Mode detection needs to treat empty string as "not set".
**Why it happens:** `core.getInput()` returns empty string for unset optional inputs (not `null`/`undefined`).
**How to avoid:** Check `!!input && input.trim() !== ''` or just `input.trim()` for truthiness when detecting mode.
**Warning signs:** Mode detection classifies empty `previous-schemas` as explicit mode.

### Pitfall 5: Stdout/Stderr Ordering in Group
**What goes wrong:** chuckd writes compatibility issues to stdout and metadata (Previous:/New:) to stderr. If you log stdout after stderr completes, the order is reversed from what users expect.
**Why it happens:** stdout and stderr are independent streams.
**How to avoid:** Stream both in real-time via the `data` events, or log stderr first (metadata) then stdout (issues) after the process closes. The CONTEXT decision is to show stderr metadata, so log stderr then stdout.

## Code Examples

Verified patterns from official sources:

### Full validate.js Structure
```javascript
// src/validate.js — complete module structure
import * as core from '@actions/core';
import { spawn } from 'node:child_process';

export function readInputs() { /* ... */ }

export function detectMode(inputs) { /* ... */ }

export function buildArgs(mode, inputs) { /* ... */ }

function runChuckd(args) {
  return new Promise((resolve) => {
    const child = spawn('chuckd', args);
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => { stdout += d; });
    child.stderr.on('data', (d) => { stderr += d; });
    child.on('close', (code) => resolve({ exitCode: code, stdout, stderr }));
    child.on('error', (err) => resolve({ exitCode: -1, stdout: '', stderr: err.message }));
  });
}

export async function runValidation() {
  const inputs = readInputs();
  let mode;
  try {
    mode = detectMode(inputs);
  } catch (err) {
    core.setFailed(err.message);
    return;
  }

  if (mode === 'git-compare') {
    core.setFailed('git-compare mode (base-ref) is not yet implemented');
    return;
  }

  const args = buildArgs(mode, inputs);
  const groupLabel = `chuckd schema validation`;

  core.startGroup(groupLabel);
  let result;
  try {
    result = await runChuckd(args);
    if (result.stderr) core.info(result.stderr);
    if (result.stdout) core.info(result.stdout);
  } finally {
    core.endGroup();
  }

  core.setOutput('exit-code', String(result.exitCode));

  if (result.exitCode === 0) {
    // Compatible — success
  } else if (result.exitCode === 1) {
    const schemaName = inputs.schemaFile || inputs.schemaPattern;
    core.error(`Schema incompatibility detected: ${schemaName} is not ${inputs.compatibility || 'FORWARD_TRANSITIVE'} compatible`);
    core.setFailed('Schema incompatibility detected');
  } else if (result.exitCode === 2) {
    core.error('Invalid arguments — check action inputs');
    core.setFailed('Invalid arguments — check action inputs');
  } else if (result.exitCode === 3) {
    core.error('Runtime error — file not found or schema parse failure');
    core.setFailed('Runtime error — file not found or schema parse failure');
  } else {
    core.error(`Unexpected exit code ${result.exitCode}`);
    core.setFailed(`Unexpected exit code ${result.exitCode}`);
  }
}
```

### action.yml Diff — Required Changes
```yaml
# REMOVE: schema-file required: true  →  required: false (or omit, defaults to false)
# ADD: schema-pattern input
# ADD: output-format input
# FIX: format default 'JSON_SCHEMA' → 'JSONSCHEMA'
# ADD: outputs section

inputs:
  schema-pattern:
    description: 'Glob pattern matching schema versions, e.g. "schemas/person.*.json"'
    required: false
    default: ''
  schema-file:
    description: 'Path to the new schema file (explicit-path or git-compare mode)'
    required: false
    default: ''
  previous-schemas:
    description: 'Space or newline-separated paths to previous schema versions'
    required: false
    default: ''
  format:
    description: 'Schema format: JSONSCHEMA, AVRO, or PROTOBUF'
    required: false
    default: 'JSONSCHEMA'   # was JSON_SCHEMA — that was wrong
  output-format:
    description: 'Output format for chuckd: TEXT or JSON'
    required: false
    default: 'TEXT'
  # ... existing: compatibility, log-level, version, base-ref, github-token unchanged

outputs:
  exit-code:
    description: 'chuckd exit code: 0=compatible, 1=incompatible, 2=usage error, 3=runtime error'
```

### Jest Test Pattern for validate.js (Mocking)
```javascript
// tests/validate.test.js
import { jest } from '@jest/globals';

// Mock @actions/core
const mockCore = {
  getInput: jest.fn(),
  setOutput: jest.fn(),
  setFailed: jest.fn(),
  startGroup: jest.fn(),
  endGroup: jest.fn(),
  error: jest.fn(),
  info: jest.fn(),
};
jest.unstable_mockModule('@actions/core', () => mockCore);

// Mock child_process spawn
const mockSpawn = jest.fn();
jest.unstable_mockModule('node:child_process', () => ({ spawn: mockSpawn }));

// Dynamic import AFTER mocks are set up
const { detectMode, buildArgs, runValidation } = await import('../src/validate.js');

describe('detectMode', () => {
  test('schema-pattern only → glob', () => {
    expect(detectMode({ schemaPattern: 'schemas/*.json', schemaFile: '', previousSchemas: '', baseRef: '' }))
      .toBe('glob');
  });
  // ...
});
```

Note: Jest ESM mocking uses `jest.unstable_mockModule` with dynamic `await import()` after mocks are registered. This is the pattern already established in the project (node --experimental-vm-modules).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ncc` CJS bundling | `rollup` ESM bundling | Phase 2 | @actions/core 3.0.0 ESM-only exports now work |
| `schema-file` required | `schema-pattern` / `schema-file` optional | Phase 3 | Enables glob mode as primary user workflow |
| No outputs declared | `exit-code` output | Phase 3 | Downstream steps can branch on compatibility result |

**Deprecated/outdated:**
- `JSON_SCHEMA` format value in action.yml default: Must be replaced with `JSONSCHEMA` (the actual CLI enum value).
- ncc: Replaced by rollup. Do not reference ncc in Phase 3 code.

## Open Questions

1. **Annotation title for incompatibility**
   - What we know: `core.error(message, properties)` accepts an optional `title` property
   - What's unclear: Whether a title is more useful than the default or adds noise
   - Recommendation: Claude's discretion — omit title for simplicity, keep just the message

2. **Streaming output vs buffered output for group**
   - What we know: spawn allows real-time streaming; both designs work
   - What's unclear: Whether real-time streaming into the group matters for user experience
   - Recommendation: Collect all output then emit after process closes (simpler code, no interleaving of stdout/stderr lines). The group provides collapsibility either way.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Jest 29.7.0 |
| Config file | `/Users/paul/Documents/Dev/Personal/chuckd-action/jest.config.js` |
| Quick run command | `npm test` (in chuckd-action dir) |
| Full suite command | `npm test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VAL-01 | `buildArgs` constructs correct `[...previous, new]` args for explicit mode | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-01 | `detectMode` returns 'explicit' when schema-file + previous-schemas set | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-01 | `detectMode` throws when no valid mode combination | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-02 | `buildArgs` maps format/compatibility/log-level to correct flags | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-02 | format default passes `JSONSCHEMA` (not `JSON_SCHEMA`) to CLI | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-03 | `runValidation` calls `core.startGroup`/`core.endGroup` | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-03 | exit 1 → `core.error` annotation + `core.setFailed` called | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-03 | exit 0 → no `core.setFailed` called | unit | `npm test -- --testPathPattern=validate` | Wave 0 |
| VAL-03 | `core.setOutput('exit-code', ...)` called with correct value | unit | `npm test -- --testPathPattern=validate` | Wave 0 |

### Sampling Rate
- **Per task commit:** `npm test`
- **Per wave merge:** `npm test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `tests/validate.test.js` — covers VAL-01, VAL-02, VAL-03 (does not exist yet)

## Sources

### Primary (HIGH confidence)
- `@actions/core` 3.0.0 type definitions — `/Users/paul/Documents/Dev/Personal/chuckd-action/node_modules/@actions/core/lib/core.d.ts` — all APIs verified: `startGroup`, `endGroup`, `error`, `setOutput`, `setFailed`, `getInput`, `info`
- `chuckd/app/src/main/java/com/anentropic/chuckd/ChuckD.java` — CLI interface verified: flag names (`-f`/`--format`, `-c`/`--compatibility`, `-l`/`--log-level`, `-o`/`--output`), exit codes (0/1/2/3), enum values (`JSONSCHEMA`, `AVRO`, `PROTOBUF`), arg order (previous... new)
- `chuckd-action/src/index.js` — current run() structure confirmed; binary on PATH by end of function
- `chuckd-action/action.yml` — current inputs confirmed; format enum mismatch (`JSON_SCHEMA` vs `JSONSCHEMA`) identified
- `chuckd-action/jest.config.js` + `tests/*.test.js` — Jest ESM pattern confirmed

### Secondary (MEDIUM confidence)
- Node.js `child_process` documentation — spawn vs exec behavior, streaming, exit code capture — [nodejs.org](https://nodejs.org/api/child_process.html)
- GitHub Actions toolkit README — startGroup/endGroup/error/setOutput APIs — [actions/toolkit](https://github.com/actions/toolkit/tree/main/packages/core)

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies installed and verified from local files
- Architecture: HIGH — chuckd CLI interface and @actions/core APIs verified from source
- Pitfalls: HIGH — format enum mismatch is a concrete bug found in actual code; other pitfalls are well-known Node.js patterns
- Test patterns: HIGH — Jest ESM mocking pattern verified from existing test files

**Research date:** 2026-03-08
**Valid until:** 2026-05-08 (stable APIs)
