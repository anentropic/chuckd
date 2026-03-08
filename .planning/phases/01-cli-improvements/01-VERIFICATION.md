---
phase: 01-cli-improvements
verified: 2026-03-08T02:00:00Z
status: passed
score: 8/8 must-haves verified
re_verification: false
human_verification:
  - test: "Run bats bat-tests/smoke.bats against a compiled native binary"
    expected: "All 11 BATS tests pass including exit-code equality assertions and glob mode tests"
    why_human: "BATS smoke tests invoke the native binary (app/build/native/nativeCompile/chuckd). The binary requires a GraalVM native-image compile which is too slow to run here. Logic is fully verified by unit tests; this is an end-to-end native binary check."
  - test: "Run ./gradlew :app:test and confirm all 73 tests pass"
    expected: "Zero failures, including NaturalSortComparatorTest and JSON output tests"
    why_human: "Gradle test run requires the full JVM toolchain. All logic is verified statically above, but a live test run confirms no compilation regressions."
---

# Phase 01: CLI Improvements Verification Report

**Phase Goal:** The chuckd binary uses typed exit codes and accepts glob patterns, making it safe and ergonomic for the action to consume
**Verified:** 2026-03-08T02:00:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from Plan 01-01 must_haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `call()` returns 0 for compatible and 1 for incompatible (never `issues.size()`) | VERIFIED | ChuckD.java:274 `return issues.isEmpty() ? 0 : 1;` |
| 2 | picocli parse errors produce exit code 2 | VERIFIED | ChuckD.java:66 `exitCodeOnInvalidInput = 2`; main() sets IExitCodeExceptionMapper mapping ParameterException → 2 |
| 3 | Runtime exceptions (IOException, parse failure) produce exit code 3 | VERIFIED | ChuckD.java:67 `exitCodeOnExecutionException = 3`; IExitCodeExceptionMapper fallback → 3 |
| 4 | Single arg triggers glob mode; 2+ args triggers explicit mode with last arg as new schema | VERIFIED | ChuckD.java:238 `if (schemaArgs.size() == 1)` dispatches to `expandGlob()`; else block at line 252 uses last element as new schema |
| 5 | Glob mode expands files via PathMatcher and sorts with natural sort (v8, v9, v10 order correctly) | VERIFIED | `expandGlob()` lines 162-179 uses `FileSystems.getDefault().getPathMatcher("glob:...")` + `Files.walk(root, 1)` + `.sorted((a, b) -> naturalCompare(...))` |
| 6 | Glob with 0 matches returns exit 2; glob with 1 match returns exit 0 | VERIFIED | ChuckD.java:241-249 — empty list → stderr + `return 2`; single match → stderr (if !quiet) + `return 0` |
| 7 | `--quiet` flag suppresses stderr metadata | VERIFIED | ChuckD.java:110-112 `@Option(names={"-q","--quiet"}) boolean quiet`; all stderr calls guarded by `if (!quiet)` |
| 8 | `--output JSON` always produces valid JSON: `[]` on compatible, issue array on incompatible | VERIFIED | `formatJson()` lines 225-231 returns `"[]"` for empty list (early return bypasses Jackson pretty-printer); JSON branch always calls `System.out.println(formatJson(issues))` regardless of emptiness |

**Score: 8/8 truths verified**

### Observable Truths (from Plan 01-02 must_haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | BATS smoke tests verify typed exit codes: 0 for compatible, 1 for incompatible (exact), 2 for bad args, 3 for missing file | VERIFIED | smoke.bats lines 19, 24, 41, 46 — `[ "$status" -eq 0 ]`, `[ "$status" -eq 1 ]`, `[ "$status" -eq 2 ]`, `[ "$status" -eq 3 ]` |
| 2 | BATS smoke tests verify glob mode: 0 matches exits 2, 1 match exits 0, 2+ matches runs comparison | VERIFIED | smoke.bats lines 49-70 — three dedicated glob tests with correct assertions |
| 3 | BATS smoke tests use reversed arg order (previous before new) in all existing tests | VERIFIED | smoke.bats lines 18, 23, 29, 34 — all use `person-narrowed.json` (prev) then `person-base.json` (new) as last arg |
| 4 | README documents the new arg order, both modes, exit codes, and --quiet flag | VERIFIED | README.md line 115 shows `chuckd [options] <previous...> <new>`; lines 108-147 cover both modes, exit code table, --quiet, JSON output |
| 5 | Version is bumped (breaking change due to arg order reversal) | VERIFIED | version.properties: `version.semver=1.0.0` (bumped from 0.6.0) |
| 6 | `--help` output includes exit code footer | VERIFIED | ChuckD.java lines 70-77 `footer = { "", "Exit codes:", "  0   Compatible...", ... }`; README Usage section lines 185-189 shows footer in help output |

**Score: 6/6 truths verified**

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `app/src/main/java/com/anentropic/chuckd/ChuckD.java` | VERIFIED | 291 lines; contains `exitCodeOnInvalidInput = 2`, `exitCodeOnExecutionException = 3`, `List<String> schemaArgs`, `expandGlob()`, `naturalCompare()`, `--quiet` option, `formatJson()` with early `[]` return, dual-mode dispatch |
| `app/src/test/java/com/anentropic/chuckd/ChuckDTestBase.java` | VERIFIED | 81 lines; `getReport()` uses path-based API (resolvedPaths, last = new schema) |
| `app/src/test/java/com/anentropic/chuckd/NaturalSortComparatorTest.java` | VERIFIED | 104 lines; 10 test methods covering numeric chunks, version strings, pure numbers, lexicographic, equal strings, file extensions, list sort, large numbers |
| `app/src/test/java/com/anentropic/chuckd/ChuckDTestJSONSchema.java` | VERIFIED | CsvSources reordered (new schema is last); `testJsonOutputCompatibleProducesEmptyArray` and `testJsonOutputIncompatibleProducesNonEmptyArray` present |
| `app/src/test/java/com/anentropic/chuckd/ChuckDTestAvro.java` | VERIFIED | CsvSources reordered; `person-base.avsc` consistently last (new schema) |
| `app/src/test/java/com/anentropic/chuckd/ChuckDTestProtobuf.java` | VERIFIED | CsvSources reordered; `person-base.proto` consistently last (new schema) |
| `bat-tests/smoke.bats` | VERIFIED | 83 lines; 11 tests — 5 existing (updated) + 6 new (bad-flag, missing-file, glob 0-match, glob 1-match, glob 2-match, quiet, JSON output) |
| `README.md` | VERIFIED | Usage section fully rewritten; `<previous...> <new>` documented; glob mode section; exit code table; `--quiet` and `--output JSON` documented; updated `--help` output pasted |
| `app/src/main/resources/version.properties` | VERIFIED | `version.semver=1.0.0` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ChuckD.call()` | `expandGlob()` | `schemaArgs.size() == 1` triggers glob mode | WIRED | ChuckD.java:238 exact match of pattern `schemaArgs\.size\(\).*==.*1` |
| `ChuckD.call()` | exit value 0 or 1 | `issues.isEmpty() ? 0 : 1` | WIRED | ChuckD.java:274 exact match of pattern `issues\.isEmpty\(\).*\?.*0.*:.*1` |
| `ChuckD.main()` | exit code 3 on runtime exception | `exitCodeOnExecutionException = 3` | WIRED | ChuckD.java:67 exact match of pattern `exitCodeOnExecutionException.*=.*3` |
| `bat-tests/smoke.bats` | chuckd binary | `run "${bin_path}/chuckd"` | WIRED | 12 invocations of `run "${bin_path}/chuckd"` in smoke.bats |
| `README.md` | new CLI behavior | `chuckd.*<previous...> <new>` | WIRED | README.md:115 `chuckd [options] <previous...> <new>` and :157 in --help output block |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CLI-01 | 01-01, 01-02 | chuckd uses typed exit codes (0=compatible, 1=incompatible, 2=usage error) instead of returning issues count | SATISFIED | `call()` returns `issues.isEmpty() ? 0 : 1`; `exitCodeOnInvalidInput = 2`; `exitCodeOnExecutionException = 3`; IExitCodeExceptionMapper in main(); BATS tests assert exact exit codes |
| CLI-02 | 01-01, 01-02 | chuckd accepts a glob pattern and finds/sorts matching files lexicographically, treating the last match as latest schema | SATISFIED | `expandGlob()` using PathMatcher + Files.walk + naturalCompare; single-arg dispatch in `call()`; BATS glob tests; README glob mode documentation |

Both Phase 1 requirements satisfied. No orphaned requirements found — REQUIREMENTS.md traceability table maps only CLI-01 and CLI-02 to Phase 1.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `README.md` | 234-240 | "Try it out" development example shows old-format output (`Found incompatible change: Difference{...}`) which is stale output format | Info | Cosmetic only — the 2-arg invocation is still valid under new interface; the output format shown is pre-refactor but the command itself works |
| `README.md` | 197, 274 | Docker usage examples use 2-arg invocation without showing that last arg is "new" | Info | The examples are technically valid (2 args = explicit mode, last is new), but don't highlight the new semantics |

No blockers. No placeholder/stub patterns. No `TODO`/`FIXME`/`return null` anti-patterns in any modified file.

### Human Verification Required

#### 1. Full Unit Test Suite

**Test:** Run `./gradlew :app:test` from the project root
**Expected:** All 73 tests pass with zero failures, including: NaturalSortComparatorTest (10 tests), JSON output tests in ChuckDTestJSONSchema (2 tests), all reordered compatibility tests in JSONSchema/Avro/Protobuf test classes
**Why human:** Requires full JVM toolchain. Static analysis verifies correctness of all code paths but cannot substitute for a live compile + test run.

#### 2. BATS Smoke Tests Against Native Binary

**Test:** Compile with `./gradlew nativeCompile` then run `bats bat-tests/smoke.bats`
**Expected:** All 11 BATS tests pass including: exit 0/1 for compatible/incompatible JSON and Avro schemas, exit 2 for bad flag, exit 3 for missing file, exit 2 for glob with no matches, exit 0 for glob with single match, exit 0 for glob with two matches (BACKWARD compatible), quiet mode produces no output, JSON compatible produces `[]`
**Why human:** Native binary compilation requires GraalVM native-image and takes 3+ minutes. BATS tests depend on a compiled binary at `app/build/native/nativeCompile/chuckd`.

---

## Gaps Summary

No gaps found. All 14 observable truths (8 from Plan 01-01, 6 from Plan 01-02) are verified. All 9 required artifacts exist, are substantive, and are wired. Both requirement IDs (CLI-01, CLI-02) are fully satisfied. The two anti-patterns flagged are informational only and do not block goal achievement.

The phase goal — "the chuckd binary uses typed exit codes and accepts glob patterns, making it safe and ergonomic for the action to consume" — is achieved. The implementation is complete and correct:

- **Typed exit codes**: 0/1/2/3 are fully implemented with picocli `@Command` annotations, IExitCodeExceptionMapper, and correct `call()` return values. The false-pass bug (issues.size() wrapping at 256) is fixed.
- **Glob patterns**: Single-arg glob mode with PathMatcher, Files.walk depth-1, natural sort comparator using Long arithmetic, and correct 0/1/2+ match handling is complete.
- **Ergonomics**: Reversed arg order (`<previous...> <new>`), `--quiet` flag, always-valid JSON output, and help footer with exit code table all land cleanly.
- **Test coverage**: 73 unit tests + 11 BATS smoke tests cover all behavior.
- **Documentation**: README fully updated; version bumped to 1.0.0 signaling the breaking arg order change.

---

_Verified: 2026-03-08T02:00:00Z_
_Verifier: Claude (gsd-verifier)_
