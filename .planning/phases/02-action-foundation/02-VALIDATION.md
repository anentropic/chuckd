---
phase: 2
slug: action-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-08
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Jest (to be installed in new chuckd-action repo) |
| **Config file** | None — Wave 0 installs |
| **Quick run command** | `npm test` |
| **Full suite command** | `npm test` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `npm run build` (confirm ncc bundle succeeds)
- **After every plan wave:** Run `npm test`
- **Before `/gsd:verify-work`:** Full suite must be green + manual smoke test on GitHub runner
- **Max feedback latency:** 5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | SETUP-01 | manual-only | — inspect action.yml | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | SETUP-02 | manual-only | — inspect action.yml | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | SETUP-03 | unit | `npm test -- --testPathPattern=version` | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 1 | BIN-01 | unit | `npm test -- --testPathPattern=platform` | ❌ W0 | ⬜ pending |
| 02-02-03 | 02 | 1 | BIN-02 | manual-only | — deferred to Phase 4 integration | ❌ W0 | ⬜ pending |
| 02-02-04 | 02 | 1 | BIN-03 | unit | `npm test -- --testPathPattern=platform` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tests/platform.test.js` — stubs for BIN-01, BIN-03
- [ ] `tests/version.test.js` — stubs for SETUP-03
- [ ] `jest.config.js` — Jest configuration
- [ ] `npm install --save-dev jest` — framework install
- [ ] Mock setup for `@actions/tool-cache` and `@actions/core`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| action.yml has marketplace fields | SETUP-01 | Static file inspection | Verify name, description, author, branding fields present |
| All inputs declared | SETUP-02 | Static file inspection | Compare inputs against requirements list |
| Binary download + PATH | BIN-02 | Requires GitHub runner | Trigger test workflow on fork, confirm `which chuckd` succeeds |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
