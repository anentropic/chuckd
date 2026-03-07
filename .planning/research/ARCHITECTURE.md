# Architecture Patterns

**Domain:** GitHub Actions composite action wrapping a CLI tool (chuckd schema validator)
**Researched:** 2026-03-07
**Confidence:** HIGH (GitHub Actions composite action structure is well-established; architecture derived from official GHA patterns and direct inspection of existing chuckd release artifacts)

---

## Recommended Architecture

### Overview

A composite action is a thin shell layer. It has no runtime of its own — it orchestrates
shell commands inside the caller's runner. The action manifest (`action.yml`) declares
inputs, optional outputs, and a sequence of steps that the GHA runner executes inline
(no container startup, no image pull).

For chuckd, the composite action is a **binary downloader + executor**: it resolves the
correct release asset for the current runner platform, downloads and unpacks it, then
invokes the `chuckd` binary with arguments derived from the action's inputs.

```
User's workflow YAML
      │  uses: anentropic/chuckd@v1
      │  with:
      │    schema-format: AVRO
      │    compatibility: BACKWARD
      │    new-schema: schemas/person.avsc
      │    previous-schema: schemas/person-v1.avsc
      ▼
GitHub Actions Runner
      │
      ├─ Resolves action from: anentropic/chuckd repo, action.yml at repo root
      │
      ├─ [Step 1] Detect platform
      │     runner.os + runner.arch → asset name suffix
      │     e.g. Linux + X64  → "Linux-x86_64"
      │          Linux + ARM64 → "Linux-aarch64"
      │          macOS + ARM64 → "macOS-aarch64"
      │
      ├─ [Step 2] Download release asset
      │     GitHub Releases API or gh CLI
      │     URL pattern: github.com/anentropic/chuckd/releases/download/{version}/chuckd-{platform}-{version}.tar.gz
      │     Writes to: $RUNNER_TEMP/chuckd/
      │
      ├─ [Step 3] Extract binary
      │     tar -xzf chuckd-{platform}-{version}.tar.gz
      │     Makes binary executable: chmod +x chuckd
      │
      ├─ [Step 4] (conditional) Resolve schema files
      │     If git-compare mode: git show {base-ref}:{path} > temp file
      │     If explicit-path mode: use input path directly
      │
      └─ [Step 5] Invoke chuckd binary
            $RUNNER_TEMP/chuckd/chuckd -f {format} -c {compatibility} {new} {previous...}
            Exit code > 0 → step fails → PR check fails
            stdout captured to log (TEXT or JSON output format)
```

---

## Component Boundaries

| Component | Location | Responsibility | Communicates With |
|-----------|----------|---------------|-------------------|
| **action.yml** | repo root (`/action.yml`) | Declares inputs, outputs, composite steps | GitHub Actions runner (reads this to execute steps) |
| **Platform detection step** | inline shell in action.yml | Maps `runner.os`/`runner.arch` to release asset name | Feeds asset-name variable to download step |
| **Binary download step** | inline shell in action.yml | Fetches and unpacks correct tarball from GitHub Releases | GitHub Releases API; writes to `$RUNNER_TEMP` |
| **Schema resolution step** | inline shell in action.yml | Optionally extracts base-branch schema via `git show` | Git (in runner workspace); produces temp file path |
| **chuckd binary** | downloaded at runtime | Validates schema compatibility, exits with count of issues | stdin: none; args: file paths + flags; stdout: TEXT or JSON report; exit code: issue count |
| **GitHub Releases** | existing, external | Hosts pre-built tarballs per platform and version | Source for binary download; already populated by build-release.yml |
| **build-release.yml** | existing CI | Builds and uploads native binaries on tag push | No change required for action milestone |

### What does NOT exist yet (to be built)

- `/action.yml` — the composite action manifest
- Platform-detection shell logic (must handle Linux-x86_64, Linux-aarch64, macOS-aarch64)
- Version-pinning strategy (how callers reference a version: `@v1` tag vs `@0.6.0`)
- Schema resolution shell logic for git-compare mode
- Integration test workflow to validate the action itself within CI

---

## Data Flow

### Input Data Flow (action inputs → chuckd CLI flags)

```
action input: schema-format   →  chuckd -f {JSONSCHEMA|AVRO|PROTOBUF}
action input: compatibility    →  chuckd -c {BACKWARD|FORWARD|FULL|..._TRANSITIVE}
action input: output-format    →  chuckd -o {TEXT|JSON}   (optional, default TEXT)
action input: new-schema       →  chuckd positional arg[0] (new schema path)
action input: previous-schema  →  chuckd positional arg[1..n] (previous schema paths)
action input: version          →  selects tarball URL from releases (default: latest)
```

### Git-Compare Mode Data Flow (conditional branch)

```
action input: base-ref         → git show {base-ref}:{previous-schema-path}
                                   → writes to $RUNNER_TEMP/chuckd/base-schema-{hash}
                                   → this temp file becomes previous-schema arg
                                   (replaces the explicit previous-schema input)
```

### Output Data Flow (chuckd binary → runner → PR check)

```
chuckd stdout  →  captured in step log (visible in Actions UI)
chuckd exit 0  →  step succeeds → check passes
chuckd exit >0 →  step fails   → PR check fails
               (exit code = number of incompatibilities, but GHA only cares 0 vs nonzero)
```

---

## Patterns to Follow

### Pattern 1: action.yml at Repo Root

**What:** The composite action manifest lives at `/action.yml` in the repo root (not in `.github/`).

**Why:** GitHub's action resolution requires `action.yml` at the root of the referenced
repository when using `uses: owner/repo@ref`. Placing it anywhere else breaks action
discovery.

**Implication for this repo:** chuckd already has source code at root — this coexists
fine. The action.yml simply lives alongside `settings.gradle`, `gradlew`, etc.

**Example:**
```
chuckd/
  action.yml          ← composite action manifest (NEW)
  app/                ← Java source
  bat-tests/          ← smoke tests
  .github/
    workflows/
      build-release.yml
      test-build.yml
      ...             ← existing CI (unchanged)
```

### Pattern 2: Use `$RUNNER_TEMP` for Downloaded Artifacts

**What:** Download and extract the chuckd binary into `$RUNNER_TEMP/chuckd/`, not the
workspace.

**Why:** `$RUNNER_TEMP` is guaranteed writable, is outside the user's workspace (no
git-tracked path collisions), and is automatically cleaned up after the job.

**Example step:**
```yaml
- name: Download chuckd binary
  shell: bash
  run: |
    mkdir -p "$RUNNER_TEMP/chuckd"
    curl -sSL "https://github.com/anentropic/chuckd/releases/download/${{ inputs.version }}/chuckd-${{ steps.platform.outputs.name }}-${{ inputs.version }}.tar.gz" \
      -o "$RUNNER_TEMP/chuckd/chuckd.tar.gz"
    tar -xzf "$RUNNER_TEMP/chuckd/chuckd.tar.gz" -C "$RUNNER_TEMP/chuckd/"
    chmod +x "$RUNNER_TEMP/chuckd/chuckd"
```

### Pattern 3: Explicit Platform Detection via runner Context

**What:** Use `${{ runner.os }}` and `${{ runner.arch }}` expressions to select the
correct binary, mapped to the naming scheme used in build-release.yml.

**Existing naming scheme (from build-release.yml):**
```
chuckd-macOS-aarch64-{version}.tar.gz    ← macOS Apple Silicon
chuckd-Linux-x86_64-{version}.tar.gz     ← standard GitHub-hosted Linux runner
chuckd-Linux-aarch64-{version}.tar.gz    ← ARM64 Linux (ubuntu-24.04-arm)
```

**Mapping logic (shell):**
```bash
OS="${{ runner.os }}"
ARCH="${{ runner.arch }}"
if [[ "$OS" == "Linux" && "$ARCH" == "X64" ]]; then
  PLATFORM="Linux-x86_64"
elif [[ "$OS" == "Linux" && "$ARCH" == "ARM64" ]]; then
  PLATFORM="Linux-aarch64"
elif [[ "$OS" == "macOS" && "$ARCH" == "ARM64" ]]; then
  PLATFORM="macOS-aarch64"
else
  echo "Unsupported platform: $OS/$ARCH" && exit 1
fi
echo "name=$PLATFORM" >> $GITHUB_OUTPUT
```

**Note:** `runner.arch` values from GitHub: `X64`, `ARM64`, `X86`. `runner.os` values:
`Linux`, `macOS`, `Windows`. Windows is explicitly unsupported (no Windows binary exists).

### Pattern 4: Version Input Defaulting to Latest Tag

**What:** Provide a `version` input that defaults to a pinned release but allows override.

**Two sub-patterns:**

Option A (simpler, recommended for v1): Default to a hardcoded version in the action.yml
`default:` field. Callers override with `version: 0.7.0`. This is predictable and
requires a new action tag for each chuckd release.

Option B: Resolve "latest" dynamically via GitHub API. More complex, adds a network
call, risk of rate limits. Skip for v1.

```yaml
inputs:
  version:
    description: 'chuckd release version to use (e.g. 0.6.0)'
    required: false
    default: '0.6.0'
```

### Pattern 5: Composite Steps Use `shell: bash` Explicitly

**What:** Every `run:` step in a composite action MUST declare `shell: bash` (or
`shell: sh`). Unlike workflow jobs, composite steps do not inherit the runner's default
shell automatically.

**Consequence of missing this:** The step fails with a cryptic error about missing shell
specification.

```yaml
runs:
  using: composite
  steps:
    - name: Detect platform
      shell: bash          # REQUIRED in composite steps
      run: |
        ...
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Docker-Based Action

**What:** Using `runs.using: docker` and pulling a container image.

**Why bad for this project:**
- Adds 30-60 seconds of container pull time per invocation
- Docker is explicitly out of scope (PROJECT.md decision)
- Composite with native binary is already faster than the Java-in-Docker approach

**Instead:** Use `runs.using: composite` and download the native binary.

### Anti-Pattern 2: Placing action.yml in a Subdirectory Without a Wrapper

**What:** Putting the composite action manifest at `.github/actions/chuckd/action.yml`.

**Why bad:** External callers cannot reference `uses: anentropic/chuckd@v1` — they would
need `uses: anentropic/chuckd/.github/actions/chuckd@v1`, which is ugly and departs
from convention. The repo is already named `chuckd`; the ergonomic reference is
`uses: anentropic/chuckd@v1`.

**Instead:** Root-level `action.yml`.

### Anti-Pattern 3: Downloading Binary to Workspace

**What:** Saving the binary to `./` or `$GITHUB_WORKSPACE`.

**Why bad:** Pollutes the user's workspace, risks conflicts with their own files, and
gets included in any subsequent `actions/upload-artifact` calls unintentionally.

**Instead:** Use `$RUNNER_TEMP` (see Pattern 2 above).

### Anti-Pattern 4: Hardcoding the Download URL Scheme

**What:** Constructing the tarball URL with string concatenation scattered across multiple
steps with no single source of truth.

**Why bad:** If the naming scheme ever changes, it must be updated in multiple places.

**Instead:** Compute the full URL in one step and pass it via `$GITHUB_OUTPUT` or a
shell variable within a single multi-line `run:` block.

### Anti-Pattern 5: Silently Passing on Unsupported Platforms

**What:** Letting the binary download silently fail (e.g., curl returns 404) and then
having the chuckd invocation fail with a confusing "file not found" error.

**Why bad:** Produces a misleading error that obscures the real problem (unsupported OS).

**Instead:** Explicit platform check with a `echo "Unsupported platform" && exit 1`
before the download attempt (see Pattern 3 mapping logic).

---

## File Structure

```
chuckd/
  action.yml                          ← NEW: composite action manifest (root level, required)
  app/                                ← existing: Java source (unchanged)
  bat-tests/
    smoke.bats                        ← existing: smoke tests (reusable for action testing)
  .github/
    workflows/
      build-release.yml               ← existing: builds+publishes native binaries (unchanged)
      test-build.yml                  ← existing: PR build+test (unchanged)
      detect-release.yml              ← existing: tag on version bump (unchanged)
      homebrew.yml                    ← existing: Homebrew formula update (unchanged)
      weekly-build.yml                ← existing: scheduled build (unchanged)
      test-action.yml                 ← NEW: integration test for the composite action itself
```

---

## Build Order (Phase Dependencies)

The components have a clear linear dependency chain:

```
1. action.yml skeleton (inputs/outputs declaration)
        │
        ▼
2. Platform detection + binary download logic
        │  (requires: knowing release asset naming from build-release.yml ✓ already known)
        ▼
3. Binary invocation logic (map inputs → CLI flags)
        │  (requires: complete understanding of chuckd CLI interface ✓ already known)
        ▼
4. Git-compare mode (git show to extract base-branch schema)
        │  (requires: working binary invocation ✓ from step 3)
        ▼
5. Action versioning / tagging strategy
        │  (requires: action.yml complete and tested)
        ▼
6. Integration test workflow (test-action.yml)
           (requires: complete action.yml + release binary available)
```

Steps 1-3 are the minimum viable action (explicit-path mode only).
Step 4 adds git-compare mode (the more user-friendly PR workflow pattern).
Steps 5-6 are release and quality gates.

---

## Scalability Considerations

This is a single-use CLI wrapper — scalability is not a concern in the traditional sense.
The relevant operational characteristics are:

| Concern | Implication | Mitigation |
|---------|-------------|------------|
| Binary download latency | ~5-10s per action invocation for tarball download from GitHub Releases | Acceptable; no caching needed for v1. `actions/cache` could be used later if latency becomes a concern. |
| GitHub Releases API rate limits | Downloading from releases.github.com has generous limits; unauthenticated downloads of public assets are unrestricted | Not a concern for typical CI usage |
| Platform coverage gaps | Only Linux-x86_64, Linux-aarch64, macOS-aarch64 are supported | Explicit failure with clear message for unsupported platforms; Windows is explicitly out of scope |
| Action version drift | If chuckd binary and action.yml version get out of sync | Pin version input in action.yml default; tag action releases to match chuckd releases |

---

## Sources

- Direct inspection of `/Users/paul/Documents/Dev/Personal/chuckd/.github/workflows/build-release.yml` — release asset naming convention confirmed (HIGH confidence)
- Direct inspection of `/Users/paul/Documents/Dev/Personal/chuckd/app/src/main/java/com/anentropic/chuckd/ChuckD.java` — CLI interface flags and exit-code behaviour confirmed (HIGH confidence)
- Direct inspection of `/Users/paul/Documents/Dev/Personal/chuckd/bat-tests/smoke.bats` — binary invocation patterns confirmed (HIGH confidence)
- GitHub Actions composite action structure: well-established pattern, knowledge cutoff August 2025; `shell: bash` requirement in composite steps is a known required field (HIGH confidence from training, unverifiable via web in this session)
- `runner.os` / `runner.arch` context values: established GHA runner context API, stable across years (HIGH confidence from training)
- `$RUNNER_TEMP` as recommended temp directory: GHA best practice, stable (HIGH confidence from training)
