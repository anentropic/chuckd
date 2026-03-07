# Roadmap: chuckd GitHub Action

## Overview

The work spans two repositories. First, two improvements to the chuckd CLI in this repo (exit code normalization and glob input). Then, in the new anentropic/chuckd-action repo, build a composite GitHub Action that downloads the right native binary, validates schemas in explicit-path mode, adds git-based PR comparison, and ships with integration tests and a release process.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: CLI Improvements** - Fix chuckd exit codes and add glob input support (this repo)
- [ ] **Phase 2: Action Foundation** - Create action.yml scaffold with input declarations, platform detection, and binary download
- [ ] **Phase 3: Core Validation** - Wire inputs to chuckd CLI in explicit-path mode; fail workflow on incompatibility
- [ ] **Phase 4: Git Compare and Release** - Add git-based schema comparison, integration tests, and versioning strategy

## Phase Details

### Phase 1: CLI Improvements
**Goal**: The chuckd binary uses typed exit codes and accepts glob patterns, making it safe and ergonomic for the action to consume
**Depends on**: Nothing (first phase)
**Requirements**: CLI-01, CLI-02
**Success Criteria** (what must be TRUE):
  1. Running chuckd with incompatible schemas exits with code 1 (not the issues count), so 256+ incompatibilities cannot produce a false 0
  2. Running chuckd with a compatible schema exits with code 0
  3. Running chuckd with invalid arguments exits with code 2
  4. User can pass a glob pattern (e.g. "schemas/person.*") and chuckd finds matching files lexicographically, treating the last match as the latest schema
**Plans**: TBD

### Phase 2: Action Foundation
**Goal**: A valid composite action exists in anentropic/chuckd-action with all inputs declared, correct marketplace metadata, and the ability to download the right chuckd binary for the runner's platform
**Depends on**: Phase 1
**Requirements**: SETUP-01, SETUP-02, SETUP-03, BIN-01, BIN-02, BIN-03
**Success Criteria** (what must be TRUE):
  1. action.yml exists at repo root with name, description, author, and branding fields that satisfy GitHub Marketplace requirements
  2. All action inputs are declared (schema file, previous schemas, format, compatibility, log level, chuckd version, base-ref)
  3. On a Linux x86_64 runner, the action downloads the Linux-x86_64 chuckd binary and places it in $RUNNER_TEMP
  4. On a Linux aarch64 runner, the action downloads the Linux-aarch64 binary
  5. On an unsupported platform, the action fails with a clear error message identifying the unsupported platform
**Plans**: TBD

### Phase 3: Core Validation
**Goal**: Users can validate schema compatibility in explicit-path mode — specifying schema files directly — and the workflow fails with log output when schemas are incompatible
**Depends on**: Phase 2
**Requirements**: VAL-01, VAL-02, VAL-03
**Success Criteria** (what must be TRUE):
  1. User specifies a new schema file and one or more previous schema file paths; the action validates compatibility between them
  2. The action maps format, compatibility, and log-level inputs to the correct chuckd CLI flags
  3. When schemas are incompatible, the workflow step fails and incompatibility details appear in the GitHub Actions log
  4. When schemas are compatible, the workflow step passes with no failure
**Plans**: TBD

### Phase 4: Git Compare and Release
**Goal**: Users can compare a schema against its version on a base branch (eliminating the need for a separate previous-schema file), and the action is fully tested and published with a stable versioning strategy
**Depends on**: Phase 3
**Requirements**: GIT-01, GIT-02, GIT-03, REL-01, REL-02, REL-03
**Success Criteria** (what must be TRUE):
  1. User specifies base-ref input and the action extracts the previous schema version from that branch automatically using git show
  2. When the checkout is shallow, the action fails with a clear message advising the user to set fetch-depth: 0
  3. Integration test workflow passes on Linux x86_64, Linux aarch64, and macOS aarch64 runners
  4. Integration test covers both explicit-path mode and git-compare mode scenarios (compatible and incompatible)
  5. The release process creates and maintains a floating v1 tag so users can pin to uses: anentropic/chuckd-action@v1
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. CLI Improvements | 0/TBD | Not started | - |
| 2. Action Foundation | 0/TBD | Not started | - |
| 3. Core Validation | 0/TBD | Not started | - |
| 4. Git Compare and Release | 0/TBD | Not started | - |
