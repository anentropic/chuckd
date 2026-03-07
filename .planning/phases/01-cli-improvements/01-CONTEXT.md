# Phase 1: CLI Improvements - Context

**Gathered:** 2026-03-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix chuckd exit codes (currently returns `issues.size()` which can wrap to 0 on 256+ issues) and add glob pattern input support. Also: reverse positional arg order, add stderr metadata, add `--quiet` flag. Bump version and update README.

</domain>

<decisions>
## Implementation Decisions

### Arg order (breaking change)
- Reverse positional arg order: `chuckd [options] <previous...> <new>` — last arg is the new schema
- This makes shell glob expansion (which produces lexicographic order) work naturally
- Both explicit mode (2+ args) and glob mode (1 arg) coexist

### Glob mode
- 1 arg = glob mode, 2+ args = explicit mode (no glob-char detection needed)
- In glob mode, user passes a quoted glob pattern (e.g. `chuckd "schemas/person.*.json"`)
- chuckd expands the glob internally using Java's PathMatcher
- Results sorted with natural sort (numeric chunks compared as numbers) so `v8, v9, v10` sort correctly without zero-padding
- Last match after natural sort = new schema, rest = previous schemas

### Glob edge cases
- 0 matches: exit code 2 + error message ("No files matched pattern: ...")
- 1 match: exit 0 (trivially compatible — no previous schema to compare against)

### Exit codes
- 0 = compatible (or trivially compatible with 1 glob match)
- 1 = incompatible
- 2 = usage error (bad args, missing files, glob matches nothing)
- 3 = runtime error (file IO failure, schema parse error)
- Override picocli's default error exit code to guarantee 2 for usage errors
- Document exit codes in both `--help` footer and README

### stderr metadata
- Both modes print file comparison info to stderr (e.g. which files matched, which is "new" vs "previous")
- New `--quiet` / `-q` flag suppresses stderr metadata
- Compatibility report (issues) stays on stdout

### Output on success
- stdout silent on compatible schemas in TEXT mode
- `--output JSON` always produces valid JSON: `[]` on compatible, issue array on incompatible

### Version and docs
- Bump version number (breaking change due to arg order reversal)
- Update README to document both modes, new arg order, exit codes, `--quiet` flag

### Claude's Discretion
- Natural sort implementation details
- Exact stderr metadata format
- How to structure the `--help` exit codes footer
- Internal refactoring needed to support both modes

</decisions>

<specifics>
## Specific Ideas

- stderr/stdout split follows common CLI pattern (curl, ffmpeg, docker) — metadata to stderr, results to stdout
- Shell glob expansion producing lexicographic order was the original implicit intent for the glob feature
- Natural sort needed because unpadded version numbers (v8, v9, v10) sort incorrectly with pure lexicographic sort

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ChuckD.java`: picocli-based CLI with `@Command`, `@Option`, `@Parameters` annotations
- `CompatibilityReporter.java`: Core comparison logic, returns `List<SchemaIncompatibility>`
- `SchemaIncompatibility` record: Used for both TEXT and JSON output formatting
- `smoke.bats`: BATS smoke tests that validate exit codes and output

### Established Patterns
- picocli for CLI parsing (options, positional params, help, version)
- `Callable<Integer>` pattern — `call()` returns exit code
- Jackson ObjectMapper for JSON serialization
- GraalVM native image compilation

### Integration Points
- `ChuckD.call()` (line 168): Currently returns `issues.size()` — needs to return typed exit codes
- `ChuckD.main()` (line 181): `CommandLine.execute()` feeds exit code to `System.exit()`
- `@Parameters` annotations (lines 95-103): Need restructuring for reversed arg order + glob mode
- `smoke.bats`: Tests need updating for new arg order and new exit code assertions

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-cli-improvements*
*Context gathered: 2026-03-07*
