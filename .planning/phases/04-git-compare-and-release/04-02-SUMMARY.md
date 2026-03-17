---
phase: 04-git-compare-and-release
plan: 02
subsystem: infra
tags: [github-actions, release-workflow, integration-test, git-compare, yaml]

# Dependency graph
requires:
  - phase: 04-git-compare-and-release
    provides: "git-compare mode implementation in chuckd-action (extractBaseRefSchema, handleExitCode)"
provides:
  - "Release workflow in chuckd-action with floating major version tag automation"
  - "chuckd-example repo scaffolded with fixture schemas and baseline git tag"
  - "3-platform integration test workflow covering explicit-path and git-compare modes"
affects: [release-process, integration-testing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Floating major version tag: git tag -fa on release published event"
    - "Two-commit git repo setup: baseline tag at v1, HEAD at v2 for git-compare tests"
    - "continue-on-error + steps.X.outcome assertion for expected-failure integration tests"
    - "3-platform matrix: ubuntu-24.04, ubuntu-24.04-arm, macos-15"

key-files:
  created:
    - "../chuckd-action/.github/workflows/release.yml"
    - "../chuckd-example/schemas/person.v1.json"
    - "../chuckd-example/schemas/person.v2.json"
    - "../chuckd-example/schemas/person.v2-breaking.json"
    - "../chuckd-example/schemas/person.json"
    - "../chuckd-example/schemas/person-breaking.json"
    - "../chuckd-example/.github/workflows/integration-test.yml"
    - "../chuckd-example/README.md"
  modified: []

key-decisions:
  - "chuckd-example uses two-commit strategy: baseline tag at v1 content, HEAD at v2/breaking content, enabling git-compare tests against baseline ref"
  - "integration-test.yml uses anentropic/chuckd-action@structured-output branch ref for pre-release testing"
  - "Expected-failure assertions use steps.X.outcome (not .conclusion) — continue-on-error: true makes .conclusion always 'success'"

patterns-established:
  - "Pattern: Two-commit repo setup with baseline tag for git-compare integration testing"
  - "Pattern: continue-on-error + outcome assertion for verifying action failure scenarios"
  - "Pattern: Floating major version tag via release.yml on release published event"

requirements-completed: [REL-01, REL-02, REL-03]

# Metrics
duration: 2min
completed: 2026-03-17
---

# Phase 4 Plan 02: Release Workflow and Integration Test Scaffold Summary

**Floating v1 tag release.yml in chuckd-action and chuckd-example repo with two-commit baseline for 3-platform integration tests covering explicit-path and git-compare modes**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-17T09:34:48Z
- **Completed:** 2026-03-17T09:37:00Z
- **Tasks:** 2 of 2 (Task 3 auto-approved checkpoint)
- **Files modified:** 8

## Accomplishments

- Created `.github/workflows/release.yml` in chuckd-action with floating major version tag logic triggered on release published events
- Initialized chuckd-example git repo with two-commit structure: `baseline` tag at v1 schemas, HEAD with v2/breaking schemas for git-compare mode testing
- Created 3-platform integration test workflow covering all 4 test scenarios: explicit-path compatible, explicit-path incompatible (expected fail), git-compare compatible, git-compare incompatible (expected fail)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create release workflow in chuckd-action** - `2a50a4d` (feat) — in chuckd-action repo
2. **Task 2: Scaffold chuckd-example repo** - `b5f54da` (initial commit, tagged baseline) + `2cca77d` (update schemas for git-compare) — in chuckd-example repo

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `/Users/paul/Documents/Dev/Personal/chuckd-action/.github/workflows/release.yml` - Floating major version tag workflow (trigger: release published)
- `/Users/paul/Documents/Dev/Personal/chuckd-example/schemas/person.v1.json` - Base v1 schema fixture for explicit-path tests
- `/Users/paul/Documents/Dev/Personal/chuckd-example/schemas/person.v2.json` - Compatible evolution schema (adds optional email)
- `/Users/paul/Documents/Dev/Personal/chuckd-example/schemas/person.v2-breaking.json` - Incompatible schema (renames name to fullName, adds required age)
- `/Users/paul/Documents/Dev/Personal/chuckd-example/schemas/person.json` - Evolving schema: v1 at baseline tag, v2 at HEAD
- `/Users/paul/Documents/Dev/Personal/chuckd-example/schemas/person-breaking.json` - Evolving schema: v1 at baseline tag, breaking at HEAD
- `/Users/paul/Documents/Dev/Personal/chuckd-example/.github/workflows/integration-test.yml` - 3-platform matrix integration test workflow
- `/Users/paul/Documents/Dev/Personal/chuckd-example/README.md` - Repo documentation

## Decisions Made

- **Two-commit strategy for chuckd-example:** All schemas start at v1 in commit 1 (tagged `baseline`). `person.json` and `person-breaking.json` are updated to v2/breaking in commit 2 (HEAD). Git-compare tests use `base-ref: baseline` to compare HEAD against the baseline tag.
- **Branch ref in integration test:** Uses `anentropic/chuckd-action@structured-output` so tests exercise the current development branch before a formal release.
- **steps.outcome not .conclusion:** Expected-failure assertions check `.outcome` because `continue-on-error: true` makes `.conclusion` always `'success'`, masking actual failure.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

**External services require manual configuration.** To complete integration testing:

1. Push chuckd-action changes to GitHub:
   ```bash
   cd /Users/paul/Documents/Dev/Personal/chuckd-action
   git push origin structured-output
   ```

2. Create the chuckd-example repo on GitHub and push (including the baseline tag):
   ```bash
   cd /Users/paul/Documents/Dev/Personal/chuckd-example
   gh repo create anentropic/chuckd-example --public --source=. --push
   git push origin baseline
   ```

3. Visit https://github.com/anentropic/chuckd-example/actions to verify the integration test workflow passes on all 3 platforms.

## Next Phase Readiness

- Phase 4 complete: git-compare mode implemented, release workflow created, integration test scaffolded
- All REL requirements addressed (REL-01, REL-02, REL-03)
- Integration tests require GitHub push to execute (user action required)
- Release workflow will trigger automatically on first release publish event

---
*Phase: 04-git-compare-and-release*
*Completed: 2026-03-17*
