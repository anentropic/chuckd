# External Integrations

**Analysis Date:** 2026-03-07

## APIs & External Services

**Schema Registry Integration:**
- Confluent Schema Registry - Schema parsing and compatibility checking
  - SDK/Client: `io.confluent:kafka-schema-registry-client` (7.8.0)
  - Integration: Embedded schema providers for multiple formats

**No Direct External API Calls:**
- Application operates in offline mode - reads schema files from local filesystem
- Does not connect to remote services at runtime
- No API keys or authentication required for basic operation

## Data Storage

**Databases:**
- None - Application is stateless and does not use any database

**File Storage:**
- Local filesystem only
- Input: Reads JSON Schema, Avro, or Protobuf schema files from disk
- Output: Console output (stdout) or JSON formatted output
- Docker volume mount: `/schemas` (for containerized execution)

**Caching:**
- None implemented

## Authentication & Identity

**Auth Provider:**
- Not applicable - No external authentication required
- Single-user CLI tool with no identity management

## Monitoring & Observability

**Error Tracking:**
- None - Application logs to stdout/stderr via Apache Log4j

**Logs:**
- Apache Log4j integration (`org.apache.log4j`)
- Configurable log levels: OFF, ALL, DEBUG, INFO, WARN, ERROR, FATAL
- Log level controlled via CLI flag: `--log-level`
- Default: OFF (no logging output)
- Log configuration: `BasicConfigurator.configure()` in `com.anentropic.chuckd.ChuckD`

## CI/CD & Deployment

**Hosting:**
- GitHub (source repository)
- Docker Hub (container images - `anentropic/chuckd`)
- GitHub Releases (binary artifacts)

**CI Pipeline:**
- GitHub Actions workflows:
  - `test-build.yml` - PR validation: JUnit tests + native image smoke tests (BATS)
  - `build-release.yml` - Release automation: multi-platform native builds + Docker image push
  - `detect-release.yml` - Automatic release detection
  - `weekly-build.yml` - Scheduled builds
  - `homebrew.yml` - Homebrew package updates

**Release Artifacts:**
- Native executable binaries (platform-specific): `chuckd-{macOS-aarch64,Linux-x86_64,Linux-aarch64}-{version}.tar.gz`
- Docker images: `anentropic/chuckd:{version}`, `anentropic/chuckd:latest`

**Deployment Platforms:**
- macOS (Apple Silicon/aarch64)
- Linux x86_64 (Intel/AMD)
- Linux aarch64 (ARM64)
- Docker containers (multi-arch)

## Environment Configuration

**Required env vars:**
- None required for core functionality

**Secrets location:**
- `${{ secrets.PERSONAL_ACCESS_TOKEN }}` - GitHub token for releases
- `${{ secrets.DOCKERHUB_ACCESS_TOKEN }}` - Docker Hub authentication
- `${{ secrets.DOCKERHUB_PASSWORD }}` - Docker Hub push credentials

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None - Application does not make outbound HTTP calls

## Dependency Management

**Package Updates:**
- Dependabot automation via `.github/dependabot.yml`
- Weekly scan for Gradle dependencies and GitHub Actions
- Groups: `java-dependencies`, `github-actions`

## Testing Integration

**Smoke Testing:**
- BATS (Bash Automated Testing System) 1.11.1 - Shell script testing for native image
- Test location: `bat-tests/smoke.bats`
- Runs against compiled native binary

**Test Reporting:**
- EnricoMi/publish-unit-test-result-action v2 - Publishes test results to PR checks
- JUnit XML format results from Gradle tests and BATS tests

## Build Integration

**Docker Build:**
- Multi-stage Dockerfile:
  1. Base: `ghcr.io/graalvm/native-image-community:21`
  2. Gradle builder stage with custom Gradle setup
  3. Final image: `scratch` (minimal base - only native binary)
- Volume support: `/schemas` directory for schema files
- Entrypoint: `chuckd` (native binary)

---

*Integration audit: 2026-03-07*
