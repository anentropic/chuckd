---
phase: 4
slug: git-compare-and-release
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-17
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Jest 29.7.0 |
| **Config file** | `chuckd-action/jest.config.js` |
| **Quick run command** | `node --experimental-vm-modules node_modules/.bin/jest --passWithNoTests` |
| **Full suite command** | `node --experimental-vm-modules node_modules/.bin/jest --passWithNoTests` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `npm test` in chuckd-action repo
- **After every plan wave:** Run `npm test` full suite
- **Before `/gsd:verify-work`:** Full Jest suite green + integration test workflow passing in chuckd-example repo
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | GIT-01 | unit | `jest tests/validate.test.js` | ✅ (add to existing) | ⬜ pending |
| 04-01-02 | 01 | 1 | GIT-02 | unit | `jest tests/validate.test.js` | ✅ (add to existing) | ⬜ pending |
| 04-01-03 | 01 | 1 | GIT-03 | unit | `jest tests/validate.test.js` | ✅ (add to existing) | ⬜ pending |
| 04-02-01 | 02 | 2 | REL-01 | integration | workflow in chuckd-example | ❌ W0 | ⬜ pending |
| 04-02-02 | 02 | 2 | REL-02 | integration | workflow in chuckd-example | ❌ W0 | ⬜ pending |
| 04-02-03 | 02 | 2 | REL-03 | manual/workflow | `.github/workflows/release.yml` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `chuckd-example` repo — create with fixture schemas and integration test workflow (REL-01, REL-02)
- [ ] `.github/workflows/release.yml` in chuckd-action repo (REL-03)
- [ ] New test cases in `tests/validate.test.js` for `extractBaseRefSchema()` and shallow clone detection (GIT-01, GIT-02, GIT-03)

*Existing Jest infrastructure covers unit test needs. Integration tests require new repo setup.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Floating v1 tag updates correctly | REL-03 | Requires actual GitHub tag operations | Push a release tag, verify v1 tag moves to new SHA |
| Action works from Marketplace ref | REL-01 | Requires published action | After release, test `uses: anentropic/chuckd-action@v1` from chuckd-example |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
