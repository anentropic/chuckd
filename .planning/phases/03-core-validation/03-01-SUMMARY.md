---
phase: 03-core-validation
plan: 01
subsystem: chuckd-action
tags: [action-yml, pure-functions, tdd, mode-detection, arg-building]
dependency_graph:
  requires: []
  provides: [action-input-contract, validate-pure-functions]
  affects: [03-02]
tech_stack:
  added: []
  patterns: [pure-functions, tdd-red-green, mode-detection]
key_files:
  created:
    - ../chuckd-action/src/validate.js
    - ../chuckd-action/tests/validate.test.js
  modified:
    - ../chuckd-action/action.yml
decisions:
  - "action.yml uses node24 runner (not composite), so no env block needed for INPUT_* vars — core.getInput() reads them natively"
  - "detectMode uses !!value.trim() pattern for emptiness checks matching core.getInput behavior"
  - "buildArgs splits previousSchemas on /\\s+/ to handle both space and newline separation"
metrics:
  duration: 2 min
  completed: 2026-03-08
  tasks_completed: 2
  files_changed: 3
---

# Phase 3 Plan 1: Action Input Contract and Pure Functions Summary

action.yml updated with glob mode inputs and outputs; validate.js pure functions (readInputs, detectMode, buildArgs) implemented with 12 new unit tests covering all modes and edge cases.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Update action.yml inputs and outputs | a084b7d | action.yml |
| 2 (RED) | Add failing tests for detectMode and buildArgs | d97bd7a | tests/validate.test.js |
| 2 (GREEN) | Implement validate.js pure functions | 9078c68 | src/validate.js |

## What Was Built

### action.yml Changes
- Added `schema-pattern` input (required: false, default: '', glob mode)
- Added `output-format` input (required: false, default: 'TEXT')
- Changed `schema-file` from `required: true` to `required: false` with `default: ''`
- Fixed `format` default from `JSON_SCHEMA` to `JSONSCHEMA` (matches chuckd CLI)
- Updated `previous-schemas` description: space or newline separated
- Added `outputs:` section with `exit-code` (0=compatible, 1=incompatible, 2=usage error, 3=runtime error)

### validate.js Pure Functions
- `readInputs()` - reads all 8 action inputs via core.getInput, returns camelCase object
- `detectMode(inputs)` - detects glob/explicit/git-compare mode from input combination
  - Throws descriptive error with guidance on invalid/conflicting inputs
  - Uses trim() for whitespace-only input handling
- `buildArgs(mode, inputs)` - builds chuckd CLI args array
  - Maps format/compatibility/logLevel/outputFormat to --flags
  - Splits previousSchemas on /\s+/ for both space and newline separation
  - Correct arg ordering: flags first, then previous files, then schema-file last
  - Omits flags when input is empty string

### Test Coverage
- 12 new tests in tests/validate.test.js
- 7 detectMode tests: all 3 valid modes + 4 error cases (no inputs, conflicting, whitespace)
- 5 buildArgs tests: glob with all flags, explicit space/newline splitting, empty flags omitted, arg ordering
- 28 total tests passing (16 existing + 12 new)

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

**Note on composite env block:** The plan mentioned adding `INPUT_SCHEMA_PATTERN` and `INPUT_OUTPUT_FORMAT` to a composite step env block. The action.yml uses `runs: using: node24` (not composite), so there is no step env block. Since `@actions/core`'s `getInput()` automatically reads `INPUT_*` environment variables set by the GitHub Actions runner for all declared inputs, no additional env mapping is needed. This is correct behavior.

## Test Results

```
Test Suites: 3 passed, 3 total
Tests:       28 passed, 28 total (16 existing + 12 new)
```

## Self-Check: PASSED

Files created/modified:
- /Users/paul/Documents/Dev/Personal/chuckd-action/action.yml - FOUND
- /Users/paul/Documents/Dev/Personal/chuckd-action/src/validate.js - FOUND
- /Users/paul/Documents/Dev/Personal/chuckd-action/tests/validate.test.js - FOUND

Commits:
- a084b7d: feat(03-01): update action.yml inputs and outputs - FOUND
- d97bd7a: test(03-01): add failing tests for detectMode and buildArgs - FOUND
- 9078c68: feat(03-01): implement validate.js pure functions - FOUND
