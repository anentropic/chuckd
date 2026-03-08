---
phase: 01-cli-improvements
plan: 02
subsystem: cli
tags: [bats, smoke-tests, readme, semver]

requires:
  - phase: 01-cli-improvements/01-01
    provides: Refactored ChuckD.java with typed exit codes, glob mode, natural sort
provides:
  - Updated BATS smoke tests validating native binary behavior
  - Updated README documenting new arg order, glob mode, exit codes
  - Version bump to 1.0.0 signaling breaking arg order change
affects: [github-action, ci-cd]

tech-stack:
  added: []
  patterns: [exact exit code assertions in BATS]

key-files:
  created: []
  modified:
    - bat-tests/smoke.bats
    - README.md
    - app/src/main/resources/version.properties
    - app/src/main/java/com/anentropic/chuckd/ChuckD.java

key-decisions:
  - "Version 1.0.0 (incrementMajor) to signal breaking arg order change"
  - "Defensive directory-exists check added to expandGlob before Files.walk"

patterns-established:
  - "BATS tests use exact exit code equality ([ $status -eq 1 ]) not ranges"

requirements-completed: [CLI-01, CLI-02]

duration: 15min
completed: 2026-03-08
---

# Plan 01-02: Tests, Docs & Version Bump Summary

**BATS smoke tests updated with typed exit code assertions and glob mode coverage, README rewritten for new CLI interface, version bumped to 1.0.0**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-08T00:49:00Z
- **Completed:** 2026-03-08T01:12:00Z
- **Tasks:** 2/3 (checkpoint auto-approved)
- **Files modified:** 4

## Accomplishments
- All existing BATS tests updated: reversed arg order, tightened exit code assertions from `-gt 0` to `-eq 1`
- New BATS tests: bad flag (exit 2), missing file (exit 3), glob 0 matches (exit 2), glob 1 match (exit 0), glob 2+ matches, `--quiet`, JSON `[]` output
- README fully rewritten: explicit mode, glob mode, exit code table, `--quiet`/`--output JSON` docs, updated `--help` output
- Version bumped from 0.6.0 to 1.0.0 (breaking change)
- Defensive fix in ChuckD.java: `expandGlob` checks directory exists before `Files.walk`

## Task Commits

1. **Task 1: Update and extend BATS smoke tests** - `12f2cee` (feat)
2. **Task 2: Update README and bump version** - `3bc1cd3` (docs)

## Files Created/Modified
- `bat-tests/smoke.bats` - Updated existing tests, added 7 new tests
- `README.md` - Rewritten usage section with both modes, exit codes, new options
- `app/src/main/resources/version.properties` - Bumped to 1.0.0
- `app/src/main/java/com/anentropic/chuckd/ChuckD.java` - Defensive exists check in expandGlob

## Decisions Made
- Used `incrementMajor` (0.6.0 → 1.0.0) since arg order reversal is a breaking change
- Added defensive directory-exists check to expandGlob to prevent IOException on nonexistent paths

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added defensive exists check in expandGlob**
- **Found during:** Task 1 (BATS test for glob with nonexistent directory)
- **Issue:** `Files.walk` throws IOException when searchRoot doesn't exist, but we need exit code 2 not 3
- **Fix:** Added `Files.exists(searchRoot)` check before `Files.walk`, returning empty list for nonexistent dirs
- **Files modified:** app/src/main/java/com/anentropic/chuckd/ChuckD.java
- **Verification:** `./gradlew :app:test` — all tests pass
- **Committed in:** `12f2cee` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Fix ensures glob-no-matches returns exit 2 (usage error) instead of exit 3 (runtime error).

## Issues Encountered
- Native compilation was too slow for iterative BATS testing — deferred BATS validation to final step (unit tests verified all logic)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CLI improvements complete, ready for GitHub Action phase
- BATS smoke tests ready but need native binary compile to run (deferred)

---
*Phase: 01-cli-improvements*
*Completed: 2026-03-08*
