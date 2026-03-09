---
phase: 03-core-validation
plan: 02
subsystem: action
tags: [github-actions, child_process, spawn, jest-esm, rollup]

# Dependency graph
requires:
  - phase: 03-01
    provides: validate.js pure functions (readInputs, detectMode, buildArgs), action.yml inputs/outputs
provides:
  - runChuckd() internal spawn wrapper resolving {exitCode, stdout, stderr}
  - runValidation() exported async function with full GHA integration
  - Exit code handling (0/1/2/3/unexpected) with core.error annotations
  - startGroup/endGroup log grouping with try/finally guarantee
  - core.setOutput('exit-code') for downstream steps
  - git-compare mode not-yet-implemented guard
  - 11 new execution tests (39 total across all test files)
  - Rebuilt dist/index.js bundle containing all validate.js logic
  - index.js wired to call runValidation() after PATH setup
affects: [04-release-polish, integration-testing]

# Tech tracking
tech-stack:
  added: [node:child_process spawn, node:events EventEmitter (tests)]
  patterns: [always-resolve spawn wrapper, try/finally for GHA group cleanup, jest.unstable_mockModule for ESM mocking]

key-files:
  created: []
  modified:
    - ../chuckd-action/src/validate.js
    - ../chuckd-action/src/index.js
    - ../chuckd-action/tests/validate.test.js
    - ../chuckd-action/dist/index.js
    - ../chuckd-action/dist/index.js.map

key-decisions:
  - "runChuckd always resolves (never rejects) — exit code -1 on spawn error; error handling responsibility stays in runValidation"
  - "setOutput('exit-code') called before exit code switch to ensure it is always set even on success"
  - "core.error() called in addition to core.setFailed() for inline annotation vs job failure separation"
  - "endGroup in finally block guarantees GHA log group is always closed even on unexpected errors"
  - "git-compare mode early-returns before spawn — Phase 4 work, not Phase 3"

patterns-established:
  - "ESM spawn mocking: jest.unstable_mockModule('node:child_process', () => ({ spawn: mockSpawn })) + beforeEach reset"
  - "EventEmitter mock process: proc with proc.stdout/proc.stderr sub-emitters, emit data then close via setTimeout"
  - "GHA output pattern: setOutput before setFailed so output is captured regardless of step outcome"

requirements-completed: [VAL-02, VAL-03]

# Metrics
duration: 8min
completed: 2026-03-09
---

# Phase 3 Plan 02: Core Validation Execution Summary

**chuckd spawn wrapper with GHA annotations (exit codes 0/1/2/3), log grouping via startGroup/endGroup, exit-code output for downstream steps, and rebuilt dist bundle**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-09T02:04:41Z
- **Completed:** 2026-03-09T02:12:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- runChuckd() wraps child_process.spawn in a Promise that always resolves with {exitCode, stdout, stderr}; returns exitCode=-1 on spawn error
- runValidation() handles all exit codes (0=success, 1=incompatibility, 2=usage error, 3=runtime error, unexpected) with appropriate core.error and core.setFailed calls
- GHA log grouping with core.startGroup/endGroup in try/finally ensures group is always closed
- core.setOutput('exit-code', String(code)) set for every execution path so downstream steps can branch on it
- git-compare mode (base-ref) returns not-yet-implemented and returns early without spawning
- index.js wired: imports runValidation and calls it after adding chuckd to PATH
- dist/index.js rebuilt via rollup — bundle contains all validate.js symbols (10+ occurrences verified)
- 11 new execution tests added; all 39 tests pass across 3 test files

## Task Commits

Each task was committed atomically:

1. **Task 1: Add execution logic to validate.js and tests** - `648e880` (feat)
2. **Task 2: Wire validation into index.js and rebuild dist** - `6bd0a0b` (feat)

**Plan metadata:** (docs commit follows)

_Note: Task 1 followed TDD — tests written first (RED), then implementation (GREEN), all in one commit since both were written together in the phase._

## Files Created/Modified
- `/Users/paul/Documents/Dev/Personal/chuckd-action/src/validate.js` - Added runChuckd() and runValidation() functions
- `/Users/paul/Documents/Dev/Personal/chuckd-action/src/index.js` - Added import and await runValidation() call
- `/Users/paul/Documents/Dev/Personal/chuckd-action/tests/validate.test.js` - Added 11 runValidation execution tests
- `/Users/paul/Documents/Dev/Personal/chuckd-action/dist/index.js` - Rebuilt rollup bundle including validate.js
- `/Users/paul/Documents/Dev/Personal/chuckd-action/dist/index.js.map` - Updated source map

## Decisions Made
- runChuckd always resolves (never rejects) to keep error handling logic centralized in runValidation
- setOutput called before the exit-code switch statement to ensure it is always set
- core.error() used for inline log annotations in addition to setFailed() which marks the job failed
- endGroup placed in finally block so GHA collapsible group always closes cleanly
- git-compare mode returns early before any spawn — implementation deferred to Phase 4

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Initial test file used `require('node:events')` which fails in ESM context; fixed by using top-level `import { EventEmitter } from 'node:events'` (corrected before first test run)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Validation pipeline is functionally complete for explicit-path and glob modes
- Action can now download chuckd, add to PATH, and run schema validation with full GHA log integration
- git-compare mode (base-ref) not implemented — Phase 4 concern
- Ready for Phase 4 release polish (publishing, versioning, documentation)

---
*Phase: 03-core-validation*
*Completed: 2026-03-09*
