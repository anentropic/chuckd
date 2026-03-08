# Phase 2: Action Foundation - Research

**Researched:** 2026-03-08
**Domain:** GitHub Actions composite action, Node.js tool-cache, GitHub Releases API
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Platform support**
- Linux x86_64 and Linux aarch64 only — macOS dropped entirely
- Unsupported platforms (macOS, Windows) fail with error: platform name + suggestion to use Docker image as alternative
- Platform mapping: GHA `X64`/`ARM64` → artifact names `x86_64`/`aarch64`

**Version resolution (A+D strategy)**
- Action stores a hardcoded compatible range (e.g. `CHUCKD_COMPAT=1.0`)
- No version input → query GitHub API for latest release matching `1.0.*`, download that
- `version: 1.0.2` input → validate against compatible range, download exact version
- Incompatible version input → fail with "chuckd X.Y.Z is not compatible with this action version. Requires 1.0.x."
- major.minor versions synced between chuckd and chuckd-action: new chuckd minor = new action minor, patch versions independent
- GitHub API called with GITHUB_TOKEN (available in workflow context, no rate limit concern)

**Binary download and caching**
- Hybrid action: composite action.yml + small JS script for download/caching
- JS script uses `@actions/tool-cache` (purpose-built for this pattern — what setup-node, setup-go use)
- Also uses `@actions/core` for inputs/outputs/logging
- Plain JavaScript, not TypeScript
- Dependencies bundled with `ncc` into single `dist/index.js` — no committed node_modules
- Binary placed in tool-cache, added to PATH

**Repo bootstrapping**
- MIT license (simpler than Apache-2.0, more common for GitHub Actions)
- Full README: usage examples, input reference, supported platforms, version compatibility
- No CI workflow in Phase 2 — deferred to Phase 4 alongside integration tests
- .gitignore for node_modules, standard Node patterns

### Claude's Discretion

- action.yml branding (icon, color)
- Exact JS script structure and error handling
- README structure and formatting
- .gitignore contents
- package.json setup

### Deferred Ideas (OUT OF SCOPE)

- macOS aarch64 platform support — dropped from scope (not needed for CI use case)
- CI workflow for action repo — Phase 4
- Integration tests — Phase 4 (REL-01, REL-02)
- Update Phase 4 REL-01: remove macOS aarch64 runner requirement
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SETUP-01 | Action has `action.yml` at repo root with name, description, and branding metadata for GitHub Marketplace | action.yml structure, branding fields, marketplace requirements documented below |
| SETUP-02 | Action declares inputs: schema file, previous schemas, format, compatibility level, log level, chuckd version, base-ref | Input declaration syntax documented below; all map to Phase 3 CLI flags |
| SETUP-03 | Action auto-resolves latest chuckd release version when version input is not specified | GitHub Releases API endpoint and filtering pattern documented below |
| BIN-01 | Action detects runner platform (Linux x86_64, Linux aarch64) via runner.os/runner.arch | `runner.arch` values X64/ARM64 confirmed; mapping to artifact names documented |
| BIN-02 | Action downloads correct native binary from GitHub releases using @actions/tool-cache | `downloadTool` + `extractTar` + `cacheDir` + `core.addPath` pattern documented |
| BIN-03 | Action fails with clear error message on unsupported platform | `core.setFailed()` pattern documented; composite env-passing pattern confirmed |
</phase_requirements>

## Summary

Phase 2 creates a new repository (`anentropic/chuckd-action`) containing a GitHub composite action. The action's `action.yml` uses `using: 'composite'` and has a single step that runs `node ${{ github.action_path }}/dist/index.js` via bash. The JS script (bundled with ncc) handles platform detection, version resolution via GitHub Releases API, binary download and caching via `@actions/tool-cache`, and adds the binary to PATH.

The architecture is well-established in the ecosystem. `@actions/tool-cache` is exactly what setup-node, setup-go, and setup-java use for this purpose. The bundling pattern (ncc → dist/index.js, committed to the repo) is GitHub's own recommended approach and avoids committing `node_modules`.

The critical architectural decision is that action inputs must be **explicitly passed as environment variables** to the Node script step in a composite action — composite actions do NOT automatically set `INPUT_*` environment variables the way JavaScript actions do. All input values are passed via the step's `env:` block.

**Primary recommendation:** Create `anentropic/chuckd-action` as a new GitHub repo. action.yml uses `using: 'composite'` with one step that runs the bundled JS via `node ${{ github.action_path }}/dist/index.js`. Bundle with `@vercel/ncc@0.38.4`. Use `@actions/tool-cache@4.0.0` and `@actions/core@3.0.0`.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `@actions/tool-cache` | 4.0.0 | Download, extract, cache, and PATH-add binary tools | Purpose-built for this pattern; used by setup-node, setup-go, setup-java |
| `@actions/core` | 3.0.0 | Read inputs, set failed, log info/warning/error, addPath | Official toolkit for action I/O and logging |
| `@vercel/ncc` | 0.38.4 | Bundle JS + node_modules into single dist/index.js | Avoids committing node_modules; GitHub's own recommended approach |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Node.js built-in `https` | stdlib | GitHub API requests (list releases, get latest) | Already available — no extra dependency needed for simple GET requests |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@actions/tool-cache` | Manual curl + mv | Tool-cache handles OS-level caching, parallel run safety, and RUNNER_TOOL_CACHE bookkeeping — don't hand-roll |
| `node` built-in https | `node-fetch` / `axios` | node built-in is sufficient for simple authenticated GET; fewer dependencies = smaller bundle |
| composite calling node | Pure JavaScript action (using: node24) | Either works; composite is locked per CONTEXT.md, and allows potential future shell steps alongside |

**Installation (in action repo):**
```bash
npm install @actions/tool-cache @actions/core
npm install --save-dev @vercel/ncc
```

## Architecture Patterns

### Recommended Project Structure (anentropic/chuckd-action repo)
```
anentropic/chuckd-action/
├── action.yml           # Composite action entry point; runs dist/index.js
├── src/
│   └── index.js         # Main script: platform detect, version resolve, download, cache
├── dist/
│   └── index.js         # ncc-bundled output — COMMITTED to repo (required for actions)
├── package.json         # name, scripts.build = "ncc build src/index.js -o dist"
├── package-lock.json    # lockfile committed
├── .gitignore           # node_modules, *.log
├── LICENSE              # MIT
└── README.md            # Usage, inputs reference, platform support, versioning
```

### Pattern 1: Composite Action Calling Node Script

**What:** action.yml uses `using: 'composite'` and runs `node ${{ github.action_path }}/dist/index.js` as a bash step, passing all inputs via environment variables.

**When to use:** When you want composite flexibility (can add shell steps later) but need the full @actions toolkit API.

**Example action.yml:**
```yaml
# Source: https://docs.github.com/en/actions/creating-actions/metadata-syntax-for-github-actions
name: 'chuckd schema compatibility check'
description: 'Validate schema evolution compatibility using chuckd'
author: 'anentropic'
branding:
  icon: 'check-circle'
  color: 'green'

inputs:
  schema-file:
    description: 'Path to the new schema file'
    required: true
  previous-schemas:
    description: 'Space-separated paths to previous schema versions'
    required: false
    default: ''
  format:
    description: 'Schema format: JSON_SCHEMA, AVRO, or PROTOBUF'
    required: false
    default: 'JSON_SCHEMA'
  compatibility:
    description: 'Compatibility mode: BACKWARD, FORWARD, FULL, BACKWARD_TRANSITIVE, FORWARD_TRANSITIVE, FULL_TRANSITIVE'
    required: false
    default: 'BACKWARD'
  log-level:
    description: 'Log level: DEBUG, INFO, WARN, ERROR'
    required: false
    default: 'WARN'
  version:
    description: 'chuckd version to download (e.g. 1.0.2). Defaults to latest compatible.'
    required: false
    default: ''
  base-ref:
    description: 'Base branch/ref for git-compare mode'
    required: false
    default: ''
  github-token:
    description: 'GitHub token for API requests'
    required: false
    default: ${{ github.token }}

runs:
  using: 'composite'
  steps:
    - name: Setup chuckd
      shell: bash
      env:
        INPUT_SCHEMA_FILE: ${{ inputs.schema-file }}
        INPUT_PREVIOUS_SCHEMAS: ${{ inputs.previous-schemas }}
        INPUT_FORMAT: ${{ inputs.format }}
        INPUT_COMPATIBILITY: ${{ inputs.compatibility }}
        INPUT_LOG_LEVEL: ${{ inputs.log-level }}
        INPUT_VERSION: ${{ inputs.version }}
        INPUT_BASE_REF: ${{ inputs.base-ref }}
        INPUT_GITHUB_TOKEN: ${{ inputs.github-token }}
      run: node ${{ github.action_path }}/dist/index.js
```

### Pattern 2: Node.js Script — Download, Cache, and PATH

**What:** JS script uses tool-cache to download the tarball, extract it, cache the binary, and add it to PATH.

**Example src/index.js:**
```javascript
// Source: https://raw.githubusercontent.com/actions/toolkit/main/packages/tool-cache/README.md
const tc = require('@actions/tool-cache');
const core = require('@actions/core');

async function run() {
  try {
    const CHUCKD_COMPAT = '1.0';

    // 1. Platform detection
    const runnerOs = process.env.RUNNER_OS;
    const runnerArch = process.env.RUNNER_ARCH;

    let platformName;
    if (runnerOs === 'Linux' && runnerArch === 'X64') {
      platformName = 'Linux-x86_64';
    } else if (runnerOs === 'Linux' && runnerArch === 'ARM64') {
      platformName = 'Linux-aarch64';
    } else {
      core.setFailed(
        `Unsupported platform: ${runnerOs}/${runnerArch}. ` +
        `Consider using the Docker image: docker pull anentropic/chuckd`
      );
      return;
    }

    // 2. Version resolution
    let version = process.env.INPUT_VERSION;
    if (!version) {
      version = await resolveLatestVersion(CHUCKD_COMPAT, process.env.INPUT_GITHUB_TOKEN);
    } else {
      validateVersion(version, CHUCKD_COMPAT);
    }

    // 3. Check tool cache first
    const toolName = 'chuckd';
    let cachedPath = tc.find(toolName, version);

    if (!cachedPath) {
      // 4. Download from GitHub releases
      const tarball = `chuckd-${platformName}-${version}.tar.gz`;
      const downloadUrl = `https://github.com/anentropic/chuckd/releases/download/${version}/${tarball}`;
      core.info(`Downloading chuckd ${version} for ${platformName}`);

      const downloadPath = await tc.downloadTool(downloadUrl, undefined, `token ${process.env.INPUT_GITHUB_TOKEN}`);
      const extractedPath = await tc.extractTar(downloadPath);
      cachedPath = await tc.cacheDir(extractedPath, toolName, version);
    } else {
      core.info(`Using cached chuckd ${version}`);
    }

    // 5. Add to PATH
    core.addPath(cachedPath);
    core.info(`chuckd ${version} available on PATH`);

  } catch (error) {
    core.setFailed(error.message);
  }
}

run();
```

### Pattern 3: GitHub Releases API — Version Resolution

**What:** Call GitHub REST API to list releases, filter by version prefix matching CHUCKD_COMPAT.

**Example resolveLatestVersion function:**
```javascript
// Source: https://docs.github.com/en/rest/releases/releases
async function resolveLatestVersion(compatRange, token) {
  // compatRange is e.g. "1.0" → match tags like "1.0.0", "1.0.1", "1.0.2"
  const https = require('https');
  const prefix = `${compatRange}.`;

  const options = {
    hostname: 'api.github.com',
    path: '/repos/anentropic/chuckd/releases?per_page=100',
    headers: {
      'Accept': 'application/vnd.github+json',
      'Authorization': `token ${token}`,
      'User-Agent': 'chuckd-action',
      'X-GitHub-Api-Version': '2022-11-28'
    }
  };

  return new Promise((resolve, reject) => {
    https.get(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        const releases = JSON.parse(data);
        // releases are returned newest first; first match wins
        const match = releases.find(r =>
          !r.draft && !r.prerelease && r.tag_name.startsWith(prefix)
        );
        if (!match) {
          reject(new Error(`No chuckd release found matching ${compatRange}.x`));
        } else {
          resolve(match.tag_name);
        }
      });
    }).on('error', reject);
  });
}

function validateVersion(version, compatRange) {
  const prefix = `${compatRange}.`;
  if (!version.startsWith(prefix)) {
    core.setFailed(
      `chuckd ${version} is not compatible with this action version. Requires ${compatRange}.x`
    );
    process.exit(1);
  }
}
```

### Pattern 4: ncc Build and Commit

**What:** ncc compiles src/index.js + all node_modules into a single dist/index.js. This file must be committed to the repo.

**package.json setup:**
```json
{
  "name": "chuckd-action",
  "version": "1.0.0",
  "description": "GitHub Action for chuckd schema compatibility checking",
  "main": "dist/index.js",
  "scripts": {
    "build": "ncc build src/index.js -o dist --minify",
    "prepare": "npm run build"
  },
  "dependencies": {
    "@actions/core": "^3.0.0",
    "@actions/tool-cache": "^4.0.0"
  },
  "devDependencies": {
    "@vercel/ncc": "^0.38.4"
  }
}
```

**Build and commit workflow:**
```bash
npm run build
git add dist/index.js
git commit -m "build: bundle dist"
```

### Anti-Patterns to Avoid

- **Committing node_modules:** Increases repo size by ~50MB+; ncc bundles everything into dist/index.js instead
- **Not committing dist/index.js:** Action will fail — GitHub runs the committed dist/ file directly; it is not built at runtime
- **Using `actions/tool-cache` 1.x or 2.x:** Current is 4.0.0; older versions have cache backend compatibility issues (GitHub's cache backend changed Feb 2025)
- **Reading inputs with `core.getInput()` in composite-called scripts:** Composite actions do NOT set `INPUT_*` env vars automatically; must pass via `env:` block in action.yml step
- **Hardcoding download URL without checking cache first:** `tc.find()` checks RUNNER_TOOL_CACHE; skip download if already cached from a previous job run on same runner

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Binary download with auth headers | Custom curl wrapper | `tc.downloadTool(url, dest, auth)` | Handles redirects, temp file placement, auth headers correctly |
| Tarball extraction | Manual tar subprocess | `tc.extractTar(path)` | Cross-platform extraction, handles temp dir creation |
| Tool caching between jobs | Custom file cache | `tc.cacheDir()` / `tc.find()` | Integrates with RUNNER_TOOL_CACHE, handles concurrent access |
| PATH manipulation | `export PATH=...` in shell | `core.addPath(path)` | Persists across steps in the job, writes to GITHUB_PATH correctly |
| Logging errors | `console.error` | `core.setFailed(message)` | Sets the action exit code AND logs as error annotation |
| Version semver comparison | Custom string comparison | String prefix matching | For this specific A+D versioning model, prefix matching on `1.0.` is correct and simple |

**Key insight:** The @actions/toolkit libraries handle the GitHub Actions environment correctly. They write to the right files (GITHUB_PATH, GITHUB_ENV), handle concurrent jobs, and integrate with the runner's tool caching. Custom shell solutions miss these details.

## Common Pitfalls

### Pitfall 1: Composite Action Inputs Not Available as INPUT_* Variables
**What goes wrong:** Script calls `core.getInput('schema-file')` but gets empty string — `core.getInput` reads `INPUT_SCHEMA_FILE` env var which is only set automatically for JavaScript actions, not composite.
**Why it happens:** `INPUT_*` injection is done by the runner only for `using: node20/node24` actions, not `using: composite`.
**How to avoid:** Pass each input explicitly via `env:` block in the composite step, then read `process.env.INPUT_SCHEMA_FILE` directly in the script, or use `core.getInput` only if you set matching `INPUT_*` vars in the step env.
**Warning signs:** All `core.getInput()` calls return empty string despite inputs being passed.

### Pitfall 2: Not Committing dist/index.js
**What goes wrong:** Action fails immediately with "cannot find entry point" or module resolution error.
**Why it happens:** GitHub runs the committed code directly — there is no npm install at runtime for composite steps running `node dist/index.js`.
**How to avoid:** Always run `npm run build` and commit `dist/index.js` before tagging a release. Add a pre-commit reminder or CI check (Phase 4).
**Warning signs:** Action fails on first step with file not found error.

### Pitfall 3: action.yml in Wrong Location
**What goes wrong:** GitHub Marketplace validation fails; users cannot reference the action.
**Why it happens:** `action.yml` must be at the repository root.
**How to avoid:** Place action.yml at repo root, not in a subdirectory.
**Warning signs:** GitHub Marketplace publish button not available; `uses: anentropic/chuckd-action@v1` gives "missing action.yml" error.

### Pitfall 4: RUNNER_OS vs runner.os Context
**What goes wrong:** Code tries to read `${{ runner.os }}` in a shell script, gets literal string.
**Why it happens:** Expression syntax `${{ }}` only works in YAML values, not shell script bodies. In the JS script, use `process.env.RUNNER_OS` and `process.env.RUNNER_ARCH` — these are automatically set by the runner.
**How to avoid:** In JS script, always use `process.env.RUNNER_OS` and `process.env.RUNNER_ARCH`.
**Warning signs:** Platform detection always falls into the unsupported branch.

### Pitfall 5: Stale dist/ After Source Change
**What goes wrong:** Bug fix applied to src/index.js but not rebuilt; old behavior persists.
**Why it happens:** dist/index.js is committed and not auto-rebuilt.
**How to avoid:** Make `npm run build` + `git add dist/` part of every development workflow. Document in CONTRIBUTING/README.
**Warning signs:** Code changes have no effect at runtime.

### Pitfall 6: GitHub API Rate Limiting (if not using token)
**What goes wrong:** Unauthenticated GitHub API calls fail after 60 requests/hour per IP (shared on GitHub-hosted runners).
**Why it happens:** GitHub-hosted runners share IP addresses; 60 req/hr is quickly exhausted.
**How to avoid:** Always use `Authorization: token ${GITHUB_TOKEN}` header — 5,000 req/hr per token.
**Warning signs:** Intermittent 403/429 errors from GitHub API in version resolution step.

### Pitfall 7: @actions/tool-cache Version Compatibility
**What goes wrong:** Using `@actions/tool-cache@1.x` or `@actions/tool-cache@2.x` with newer runner agent causes cache backend errors.
**Why it happens:** GitHub changed their cache backend in February 2025; old versions of tool-cache use deprecated API.
**How to avoid:** Use `@actions/tool-cache@^4.0.0` (current is 4.0.0).
**Warning signs:** Cache operations succeed but data is never found on subsequent runs.

## Code Examples

### Platform Detection with Environment Variables (verified pattern)
```javascript
// Source: https://docs.github.com/en/actions/concepts/workflows-and-actions/contexts
// runner.arch values: X86, X64, ARM, ARM64
// runner.os values: Linux, Windows, macOS
// These are available as RUNNER_OS and RUNNER_ARCH environment variables in all step types

const PLATFORM_MAP = {
  'Linux/X64':   'Linux-x86_64',
  'Linux/ARM64': 'Linux-aarch64',
};

const key = `${process.env.RUNNER_OS}/${process.env.RUNNER_ARCH}`;
const platformName = PLATFORM_MAP[key];

if (!platformName) {
  core.setFailed(
    `Unsupported platform: ${process.env.RUNNER_OS}/${process.env.RUNNER_ARCH}. ` +
    `Use Docker image instead: docker pull anentropic/chuckd`
  );
  return;
}
```

### Artifact Name Pattern (confirmed from build-release.yml)
```javascript
// Confirmed from .github/workflows/build-release.yml matrix:
//   platform-name: Linux-x86_64  (ubuntu-latest, X64)
//   platform-name: Linux-aarch64 (ubuntu-24.04-arm, ARM64)
// Artifact: chuckd-{platform-name}-{version}.tar.gz
// Example: chuckd-Linux-x86_64-1.0.2.tar.gz

const tarball = `chuckd-${platformName}-${version}.tar.gz`;
const downloadUrl = `https://github.com/anentropic/chuckd/releases/download/${version}/${tarball}`;
```

### tool-cache: Full Download + Cache + PATH Pattern
```javascript
// Source: https://raw.githubusercontent.com/actions/toolkit/main/packages/tool-cache/README.md
const tc = require('@actions/tool-cache');
const core = require('@actions/core');

// Check cache first (keyed by tool name + version)
let toolDir = tc.find('chuckd', version);

if (!toolDir) {
  // Download the tarball
  const downloadPath = await tc.downloadTool(
    downloadUrl,
    undefined,                           // dest (let tool-cache choose temp path)
    `token ${process.env.INPUT_GITHUB_TOKEN}`  // auth header
  );

  // Extract .tar.gz
  const extractedDir = await tc.extractTar(downloadPath);

  // Cache for future use
  toolDir = await tc.cacheDir(extractedDir, 'chuckd', version);
}

// Add binary directory to PATH for remaining steps
core.addPath(toolDir);
```

### GitHub Releases API — List Releases
```javascript
// Source: https://docs.github.com/en/rest/releases/releases
// GET /repos/{owner}/{repo}/releases
// Returns array sorted newest first, each with: tag_name, draft, prerelease, assets[]
// assets[].browser_download_url is the direct download URL
// Use Authorization: token {GITHUB_TOKEN} for 5000 req/hr rate limit

const url = 'https://api.github.com/repos/anentropic/chuckd/releases?per_page=100';
// Response: [{ tag_name: "1.0.2", draft: false, prerelease: false, assets: [...] }, ...]
// Filter: tag_name.startsWith("1.0.") && !draft && !prerelease
// First match = latest stable patch for 1.0.x
```

### Passing Inputs Through env: Block in Composite Action
```yaml
# Source: https://github.com/orgs/community/discussions/26248
# Composite actions do NOT auto-inject INPUT_* env vars — must be explicit
runs:
  using: 'composite'
  steps:
    - name: Setup chuckd
      shell: bash
      env:
        INPUT_VERSION: ${{ inputs.version }}
        INPUT_GITHUB_TOKEN: ${{ inputs.github-token }}
        # ... all other inputs
      run: node ${{ github.action_path }}/dist/index.js
```

### action.yml Branding (Claude's Discretion)
```yaml
# Feather v4.28.0 icons available: check, check-circle, shield, terminal, etc.
# Excluded icons: coffee, columns, divide-circle, divide-square, divide, frown,
#   hexagon, key, meh, mouse-pointer, smile, tool, x-octagon
# Colors: white, black, yellow, blue, green, orange, red, purple, gray-dark
branding:
  icon: 'check-circle'   # Suggests validation/verification
  color: 'green'         # Success/pass association
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `using: node16` | `using: node20` → `node24` now recommended | Sep 2025 (node20 deprecated) | Use `node24` for new JavaScript actions; composite calling node uses pre-installed Node |
| `@actions/tool-cache@1.x/2.x` | `@actions/tool-cache@4.0.0` | Feb 2025 (cache backend change) | Old versions fail with deprecated cache backend |
| Committing `node_modules` | Bundling with `@vercel/ncc` into `dist/index.js` | 2020+ standard practice | Much smaller repo; single file to commit |
| `actions/checkout@v3` | `actions/checkout@v6` | Current (confirmed in chuckd repo workflows) | Already using v6 |

**Deprecated/outdated:**
- `using: node12`, `using: node16`: deprecated, runners removing support
- `@actions/cache` < 4.0.0: deprecated, removed February 2025
- Committing `node_modules`: not recommended, large repo footprint

## Open Questions

1. **Node.js availability in composite step**
   - What we know: GitHub-hosted ubuntu runners have Node.js pre-installed; `node` command is on PATH
   - What's unclear: The exact Node.js version available varies; currently v20 default, transitioning to v24
   - Recommendation: `node ${{ github.action_path }}/dist/index.js` works regardless — ncc bundles CJS compatible with whatever Node is pre-installed. No `setup-node` step needed.

2. **`@actions/tool-cache@4.0.0` API surface changes from 3.x**
   - What we know: 4.0.0 was very recently released (search result said "5 hours ago" on search date); `@actions/core` is 3.0.0
   - What's unclear: Whether there are breaking API changes vs 3.x
   - Recommendation: Use the documented API (`downloadTool`, `extractTar`, `cacheDir`, `find`, `addPath`) — these are stable. If 4.0.0 has issues, pin to 3.x.

3. **`tc.downloadTool` auth header format**
   - What we know: Third argument is an auth string; common values are `token ${GITHUB_TOKEN}` and `Bearer ${GITHUB_TOKEN}`
   - What's unclear: Which format tool-cache expects for GitHub release downloads
   - Recommendation: Use `token ${process.env.INPUT_GITHUB_TOKEN}` — this is the PAT format GitHub uses; if issues arise, try `Bearer`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Jest (to be installed) |
| Config file | None — Wave 0 gap |
| Quick run command | `npm test` (after jest setup) |
| Full suite command | `npm test` |

**Note:** The chuckd-action repo is a new repo — no test infrastructure exists yet. The Java/Gradle test infrastructure in the chuckd repo does NOT apply here. Phase 2 work lives in the new `anentropic/chuckd-action` repo. Validation of Phase 2 is **primarily manual** (smoke test by calling the action) since integration tests are deferred to Phase 4. Unit tests for the JS business logic (platform mapping, version validation, version resolution logic) are feasible but the test framework needs to be set up in Wave 0.

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SETUP-01 | action.yml has required marketplace fields | manual-only | — inspect file | ❌ Wave 0 (manual review) |
| SETUP-02 | All inputs declared in action.yml | manual-only | — inspect file | ❌ Wave 0 (manual review) |
| SETUP-03 | Version resolution finds latest 1.0.x release | unit | `npm test -- --testPathPattern=version` | ❌ Wave 0 |
| BIN-01 | Platform detection maps X64→x86_64, ARM64→aarch64 | unit | `npm test -- --testPathPattern=platform` | ❌ Wave 0 |
| BIN-02 | Binary download and PATH placement | manual-only / integration | — deferred Phase 4 | ❌ Wave 0 (integration deferred) |
| BIN-03 | Unsupported platform fails with clear message | unit | `npm test -- --testPathPattern=platform` | ❌ Wave 0 |

**Manual verification trigger:** After Phase 2 implementation, manually trigger a test workflow in a fork that uses `anentropic/chuckd-action@main` on a linux runner, confirm chuckd is on PATH.

### Sampling Rate
- **Per task commit:** `npm run build` to confirm bundle succeeds (no test runner yet)
- **Per wave merge:** `npm test` (once Jest installed in Wave 0)
- **Phase gate:** Manual smoke test (run action on actual GitHub runner) before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `tests/platform.test.js` — covers BIN-01, BIN-03
- [ ] `tests/version.test.js` — covers SETUP-03
- [ ] `jest.config.js` — Jest configuration
- [ ] Framework install: `npm install --save-dev jest` — if none detected
- [ ] Consider mocking `@actions/tool-cache` and `@actions/core` for unit tests

## Sources

### Primary (HIGH confidence)
- Official GHA Docs — action.yml metadata syntax, branding options, composite action structure
- Official GHA Docs — runner context values (runner.os, runner.arch confirmed values)
- `actions/toolkit` GitHub (raw package.json) — `@actions/tool-cache@4.0.0`, `@actions/core@3.0.0` verified versions
- `@vercel/ncc` GitHub — current version 0.38.4, basic build command
- `build-release.yml` in chuckd repo — artifact naming convention `chuckd-{platform-name}-{version}.tar.gz` confirmed

### Secondary (MEDIUM confidence)
- WebFetch: `@actions/tool-cache` README — downloadTool, extractTar, cacheDir, find API signatures
- WebFetch: `@actions/core` README — getInput, setFailed, info, addPath API signatures
- WebFetch: GHA community discussion #26248 — composite calling `node ${{ github.action_path }}/dist/index.js` pattern
- WebFetch: `actions/setup-go` action.yml — `using: node24` confirmed for modern actions
- WebSearch: GitHub Changelog Sept 2025 — node20 deprecation, node24 now supported

### Tertiary (LOW confidence)
- WebSearch: `@actions/tool-cache@3.0.0` vs 4.0.0 breaking changes — npm search said 4.0.0 exists but no changelog found; use documented API only
- WebSearch: `tc.downloadTool` third-argument auth format — community examples use `token ${TOKEN}` but not confirmed in official docs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — package versions confirmed via raw package.json files; ncc version from official repo
- Architecture: HIGH — composite + node pattern confirmed from multiple official and community sources; artifact naming confirmed from existing workflow
- Pitfalls: HIGH — INPUT_* env var pitfall is documented in GitHub runner issue tracker; others derived from well-understood mechanics

**Research date:** 2026-03-08
**Valid until:** 2026-06-08 (stable APIs; node24 transition timeline may shift sooner)
