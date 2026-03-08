---
phase: 01-cli-improvements
plan: 01
subsystem: cli
tags: [java, picocli, glob, natural-sort, exit-codes, pathfinder]

# Dependency graph
requires: []
provides:
  - Typed exit codes (0=compatible, 1=incompatible, 2=usage error, 3=runtime error) in ChuckD CLI
  - Single-arg glob mode with natural sort via Java NIO PathMatcher
  - Reversed positional arg order: last arg is new schema (enables shell glob expansion)
  - --quiet/-q flag to suppress stderr metadata
  - JSON mode always produces valid JSON ([] on compatible)
  - NaturalSortComparatorTest unit tests
  - JSON output unit tests
affects: [02-github-action, smoke-tests, README]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single List<String> schemaArgs with arity 1..* for dual-mode dispatch (1=glob, 2+=explicit)"
    - "naturalCompare() with Long.parseLong for numeric chunks — avoids Integer overflow"
    - "PathMatcher glob: prefix + Files.walk(root, 1) for depth-1 glob expansion"
    - "formatJson() returns compact '[]' for empty list, pretty-printed array for non-empty"

key-files:
  created:
    - app/src/test/java/com/anentropic/chuckd/NaturalSortComparatorTest.java
  modified:
    - app/src/main/java/com/anentropic/chuckd/ChuckD.java
    - app/src/test/java/com/anentropic/chuckd/ChuckDTestBase.java
    - app/src/test/java/com/anentropic/chuckd/ChuckDTestJSONSchema.java
    - app/src/test/java/com/anentropic/chuckd/ChuckDTestAvro.java
    - app/src/test/java/com/anentropic/chuckd/ChuckDTestProtobuf.java

key-decisions:
  - "Committed both Task 1 and Task 2 atomically (single commit) since ChuckD.java and test updates are interdependent"
  - "Fixed formatJson() to return compact '[]' (not pretty '[ ]') for empty list, matching spec"
  - "getStructuredReport() now takes explicit (newSchemaPath, previousPaths) params instead of using picocli-bound fields — cleaner API for tests"
  - "expandGlob() uses depth-1 walk (Files.walk(root, 1)) — matches flat directory glob pattern from spec; ** support deferred"

patterns-established:
  - "Dual-mode dispatch: schemaArgs.size() == 1 triggers glob mode, 2+ triggers explicit mode"
  - "Test CsvSource order: previous schemas first, new schema last (mirrors new CLI arg order)"
  - "naturalCompare() method is package-level static on ChuckD for direct unit testing"

requirements-completed: [CLI-01, CLI-02]

# Metrics
duration: 5min
completed: 2026-03-08
---

# Phase 01: CLI Improvements Plan 01 Summary

**Typed exit codes (0/1/2/3), reversed arg order, single-arg glob with natural sort, --quiet flag, and always-valid JSON output in ChuckD CLI**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-08T00:16:17Z
- **Completed:** 2026-03-08T00:21:44Z
- **Tasks:** 2 (committed atomically)
- **Files modified:** 6

## Accomplishments
- Refactored ChuckD.java: single `List<String> schemaArgs` replaces dual `@Parameters` fields; typed exit codes (0/1/2/3) via `@Command` annotations; `expandGlob()` with PathMatcher; `naturalCompare()` with Long-safe numeric chunks; `--quiet` flag; reversed arg semantics
- Fixed false-pass bug: `call()` now returns `issues.isEmpty() ? 0 : 1` instead of `issues.size()` (which could wrap to 0 at 256 issues)
- Fixed JSON mode: `formatJson()` returns compact `[]` for empty list (not Jackson's `[ ]`); JSON mode always writes output even on compatible schemas
- Updated all three test classes (JSONSchema, Avro, Protobuf) for reversed arg order; all CsvSource rows reordered so new schema is last
- Added `NaturalSortComparatorTest` (10 test cases) covering numeric chunk ordering, version names, file names, equal strings, and large numbers
- Added JSON output tests verifying `[]` on compatible and non-empty array on incompatible schemas
- 73 tests total, all passing

## Task Commits

Both tasks committed atomically (interdependent: ChuckD.java change breaks tests until tests are updated):

1. **Tasks 1+2: Refactor ChuckD CLI and update tests** - `66dcd83` (feat)

## Files Created/Modified
- `app/src/main/java/com/anentropic/chuckd/ChuckD.java` - Refactored CLI with typed exit codes, glob mode, natural sort, quiet flag, reversed args
- `app/src/test/java/com/anentropic/chuckd/ChuckDTestBase.java` - Updated getReport() to use path-based getStructuredReport() API
- `app/src/test/java/com/anentropic/chuckd/ChuckDTestJSONSchema.java` - Reordered CsvSource for new arg order; added JSON output tests
- `app/src/test/java/com/anentropic/chuckd/ChuckDTestAvro.java` - Reordered CsvSource for new arg order
- `app/src/test/java/com/anentropic/chuckd/ChuckDTestProtobuf.java` - Reordered CsvSource for new arg order
- `app/src/test/java/com/anentropic/chuckd/NaturalSortComparatorTest.java` - New: unit tests for naturalCompare()

## Decisions Made
- **Atomic commit for Tasks 1+2:** The plan noted tests "happen atomically" with ChuckD changes since they're tightly coupled. A single commit avoids a broken-tests intermediate state.
- **formatJson() compact empty output:** Jackson's pretty printer outputs `[ ]` for empty lists. Fixed to return literal `"[]"` to match the spec requirement and avoid confusing downstream JSON consumers.
- **getStructuredReport() signature change:** Changed from no-args (using picocli-bound fields) to `(Path newSchemaPath, List<Path> previousPaths)`. This makes the method testable independently of picocli arg parsing and eliminates the need for tests to mock the `schemaArgs` field.
- **depth-1 glob walk:** Used `Files.walk(root, 1)` rather than unlimited depth. Matches the flat-directory glob pattern in the spec (`"schemas/person.*.json"`). Recursive `**` support deferred.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Jackson pretty printer outputting `[ ]` for empty JSON array**
- **Found during:** Task 2 (JSON output test)
- **Issue:** `mapper.writerWithDefaultPrettyPrinter().writeValueAsString([])` returns `"[ ]"` not `"[]"`. Test asserting `assertEquals("[]", output)` failed.
- **Fix:** Added early return `"[]"` in `formatJson()` when list is empty; pretty printer only used for non-empty lists.
- **Files modified:** `app/src/main/java/com/anentropic/chuckd/ChuckD.java`
- **Verification:** `testJsonOutputCompatibleProducesEmptyArray` passes
- **Committed in:** 66dcd83 (part of task commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Minor fix needed for Jackson pretty-printer behavior on empty arrays. No scope creep.

## Issues Encountered
None beyond the Jackson pretty-printer issue documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ChuckD binary CLI is ready for integration tests (smoke.bats needs updating for new arg order and exit codes — deferred to a future plan or wave as noted in RESEARCH.md)
- Exit code contract is now well-defined and tested; GitHub Action (Phase 2) can rely on 0/1/2/3 semantics
- Breaking change: arg order reversed; callers must update to `<previous...> <new>` order

---
*Phase: 01-cli-improvements*
*Completed: 2026-03-08*
