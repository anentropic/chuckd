# Requirements: chuckd GitHub Action

**Defined:** 2026-03-07
**Core Value:** Developers can validate schema evolution compatibility in their CI/CD pipeline without running a full Schema Registry.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Action Setup

- [x] **SETUP-01**: Action has `action.yml` at repo root with name, description, and branding metadata for GitHub Marketplace
- [x] **SETUP-02**: Action declares inputs: schema file, previous schemas, format, compatibility level, log level, chuckd version, base-ref
- [ ] **SETUP-03**: Action auto-resolves latest chuckd release version when version input is not specified

### Binary Management

- [ ] **BIN-01**: Action detects runner platform (Linux x86_64, Linux aarch64, macOS aarch64) via runner.os/runner.arch
- [ ] **BIN-02**: Action downloads correct native binary from GitHub releases using curl and tar
- [ ] **BIN-03**: Action fails with clear error message on unsupported platform

### CLI Improvements

- [x] **CLI-01**: chuckd uses typed exit codes (0=compatible, 1=incompatible, 2=usage error) instead of returning issues count
- [x] **CLI-02**: chuckd accepts a glob pattern (e.g. "schemas/person.*") and finds/sorts matching files lexicographically, treating the last match as latest schema

### Schema Validation — Explicit Path Mode

- [x] **VAL-01**: User can specify schema file and one or more previous schema file paths for comparison
- [x] **VAL-02**: Action maps format, compatibility, and log-level inputs to chuckd CLI flags
- [x] **VAL-03**: Action outputs incompatibility details to GitHub Actions logs on failure

### Schema Validation — Git Compare Mode

- [ ] **GIT-01**: User can specify base-ref input to compare schema against the same file on a different branch
- [ ] **GIT-02**: Action extracts previous schema version from base branch using git show
- [ ] **GIT-03**: Action detects shallow clone and fails with clear message advising fetch-depth: 0

### Release & Testing

- [ ] **REL-01**: Integration test workflow validates action on Linux x86_64, Linux aarch64, and macOS aarch64 runners
- [ ] **REL-02**: Integration test covers both explicit-path mode and git-compare mode
- [ ] **REL-03**: Release process includes floating v1 tag that tracks latest release

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Enhanced Output

- **OUT-01**: Action posts PR comment with incompatibility details
- **OUT-02**: Action outputs structured data (JSON) as GitHub Action output for downstream steps

### Enhanced Detection

- **DET-01**: Action auto-detects changed schema files in PR diff

## Out of Scope

| Feature | Reason |
|---------|--------|
| PR comment posting | Requires write permissions, breaks for fork PRs, adds complexity |
| Auto-detection of changed schema files | Creates false confidence, repo-structure assumptions |
| Docker-based action | Composite action with native binary is faster and simpler |
| Action in chuckd repo | Independent versioning requires separate repo (anentropic/chuckd-action) |
| Windows runner support | No Windows native binary exists |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-01 | Phase 1 | Complete |
| CLI-02 | Phase 1 | Complete |
| SETUP-01 | Phase 2 | Complete |
| SETUP-02 | Phase 2 | Complete |
| SETUP-03 | Phase 2 | Pending |
| BIN-01 | Phase 2 | Pending |
| BIN-02 | Phase 2 | Pending |
| BIN-03 | Phase 2 | Pending |
| VAL-01 | Phase 3 | Complete |
| VAL-02 | Phase 3 | Complete |
| VAL-03 | Phase 3 | Complete |
| GIT-01 | Phase 4 | Pending |
| GIT-02 | Phase 4 | Pending |
| GIT-03 | Phase 4 | Pending |
| REL-01 | Phase 4 | Pending |
| REL-02 | Phase 4 | Pending |
| REL-03 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 17 total
- Mapped to phases: 17
- Unmapped: 0

---
*Requirements defined: 2026-03-07*
*Last updated: 2026-03-09 after plan 03-02 completion*
