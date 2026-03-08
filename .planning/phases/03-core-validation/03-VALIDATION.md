---
phase: 3
slug: core-validation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-08
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Jest 29.7.0 |
| **Config file** | `chuckd-action/jest.config.js` |
| **Quick run command** | `npm test` (in chuckd-action dir) |
| **Full suite command** | `npm test` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `npm test`
- **After every plan wave:** Run `npm test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 0 | VAL-01, VAL-02, VAL-03 | unit | `npm test -- --testPathPattern=validate` | Wave 0 | pending |
| 03-01-02 | 01 | 1 | VAL-01 | unit | `npm test -- --testPathPattern=validate` | Wave 0 | pending |
| 03-01-03 | 01 | 1 | VAL-02 | unit | `npm test -- --testPathPattern=validate` | Wave 0 | pending |
| 03-01-04 | 01 | 1 | VAL-03 | unit | `npm test -- --testPathPattern=validate` | Wave 0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `tests/validate.test.js` — test stubs for VAL-01, VAL-02, VAL-03

*Existing infrastructure (Jest, jest.config.js) covers framework needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GHA log annotations render correctly | VAL-03 | Requires actual GitHub Actions runner | Push to branch, trigger workflow, inspect Actions log |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
