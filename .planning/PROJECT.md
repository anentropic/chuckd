# chuckd

## What This Is

A schema evolution validation tool that checks compatibility between versions of JSON Schema, Avro, and Protobuf schemas. Used in CI/CD pipelines to ensure schema changes don't break existing clients/consumers. Distributed as native binaries (via GraalVM), Docker image, and Homebrew formula.

## Core Value

Developers can validate schema evolution compatibility in their CI/CD pipeline without running a full Schema Registry.

## Requirements

### Validated

- Schema compatibility checking for JSON Schema, Avro, and Protobuf formats — existing
- Multiple compatibility modes (BACKWARD, FORWARD, FULL, transitive variants) — existing
- CLI interface with picocli (schema files as args, flags for format/compatibility/output) — existing
- Structured output in TEXT and JSON formats — existing
- GraalVM native image compilation for standalone binaries — existing
- Docker image (multi-arch: amd64/arm64) — existing
- Homebrew formula distribution — existing
- GitHub Actions CI/CD for testing and release builds — existing
- BATS smoke tests for native binary validation — existing

### Active

- [ ] GitHub Action (composite) that runs chuckd to validate schema compatibility in PRs
- [ ] Action uses native binary from GitHub releases (not Docker)
- [ ] User configures schema file paths in action inputs
- [ ] Support git-based comparison (PR schema vs base branch version)
- [ ] Support explicit previous version paths (user-managed schema history)
- [ ] Fails the PR check on incompatibility, with details in logs

### Out of Scope

- PR comment posting with incompatibility details — keep it simple for v1, just fail the check
- Auto-detection of changed schema files — user configures paths explicitly
- Separate repo for the action — lives in this repo
- Docker-based action — using composite action with native binary instead

## Context

- Validation logic is borrowed from Confluent Schema Registry, re-wrapped as CLI
- Native binaries already built and published to GitHub releases for Linux x86_64, Linux aarch64, and macOS aarch64
- Docker image published to Docker Hub at anentropic/chuckd
- Current version: 0.6.0
- Existing CI already uses `graalvm/setup-graalvm@v1` with java-version 21
- Composite action will need to download the right binary for the runner's platform

## Constraints

- **Distribution**: Action must work with existing release binary artifacts (no new build infrastructure)
- **Platform**: GitHub Actions runners are Linux x86_64 (standard) or Linux aarch64 (larger runners), need to handle both
- **Repo structure**: Action lives in this repo alongside the CLI source code

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Composite action over Docker action | Faster startup, uses existing native binaries from releases | -- Pending |
| Action in same repo as CLI | Simpler maintenance, single release process | -- Pending |
| Fail check only (no PR comments) | Keep v1 simple, log output sufficient | -- Pending |
| User-configured paths (no auto-detect) | More predictable, works with any repo structure | -- Pending |

---
*Last updated: 2026-03-07 after initialization*
