---
phase: 1
slug: cli-improvements
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-08
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.0.3 (unit tests) + BATS (smoke tests on native binary) |
| **Config file** | `app/build.gradle` — `test { useJUnitPlatform() }` |
| **Quick run command** | `./gradlew :app:test` |
| **Full suite command** | `./gradlew :app:test && bats bat-tests/smoke.bats` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:test`
- **After every plan wave:** Run `./gradlew :app:test && bats bat-tests/smoke.bats`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | CLI-01 | smoke | `bats bat-tests/smoke.bats` | ✅ (update existing) | ⬜ pending |
| 1-01-02 | 01 | 1 | CLI-01 | smoke | `bats bat-tests/smoke.bats` | ✅ (update `$status -gt 0` → `$status -eq 1`) | ⬜ pending |
| 1-01-03 | 01 | 1 | CLI-01 | smoke | `bats bat-tests/smoke.bats` | ❌ W0 | ⬜ pending |
| 1-01-04 | 01 | 1 | CLI-01 | smoke | `bats bat-tests/smoke.bats` | ❌ W0 | ⬜ pending |
| 1-01-05 | 01 | 1 | CLI-01 | unit | `./gradlew :app:test` | ❌ W0 | ⬜ pending |
| 1-02-01 | 02 | 1 | CLI-02 | unit | `./gradlew :app:test` | ❌ W0 | ⬜ pending |
| 1-02-02 | 02 | 1 | CLI-02 | smoke | `bats bat-tests/smoke.bats` | ❌ W0 | ⬜ pending |
| 1-02-03 | 02 | 1 | CLI-02 | smoke | `bats bat-tests/smoke.bats` | ❌ W0 | ⬜ pending |
| 1-02-04 | 02 | 1 | CLI-02 | unit | `./gradlew :app:test` | ❌ W0 | ⬜ pending |
| 1-02-05 | 02 | 1 | CLI-02 | unit | `./gradlew :app:test` | ✅ (update existing tests) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] New BATS tests in `bat-tests/smoke.bats` — exit code 2 (bad args) and exit code 3 (file-not-found)
- [ ] New unit test for `--output JSON` producing `[]` on compatible schemas
- [ ] New unit test for `expandGlob()` logic and natural sort comparator
- [ ] New BATS tests for glob mode: 0 matches → exit 2, 1 match → exit 0
- [ ] Update existing `ChuckDTest*` arg order — current tests pass `new` as first arg; after reversal `new` must be last
- [ ] Update existing BATS `[ "$status" -gt 0 ]` to `[ "$status" -eq 1 ]` and reverse file arg order

*Existing infrastructure covers framework installation — JUnit and BATS already available.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `--help` footer shows exit codes | CLI-01 | Visual output verification | Run `chuckd --help` and verify exit code table appears in footer |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
