# Phase 1: CLI Improvements - Research

**Researched:** 2026-03-08
**Domain:** Java CLI (picocli 4.7.7), Java NIO glob/PathMatcher, BATS smoke testing
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Arg order (breaking change):** Reverse positional arg order to `chuckd [options] <previous...> <new>` — last arg is the new schema. Makes shell glob expansion work naturally.
- **Mode dispatch:** 1 arg = glob mode, 2+ args = explicit mode (no glob-char detection needed).
- **Glob mode:** User passes a quoted glob pattern (e.g. `chuckd "schemas/person.*.json"`); chuckd expands internally using Java's PathMatcher. Results sorted with natural sort (numeric chunks compared as numbers) so `v8, v9, v10` sort correctly without zero-padding.
- **Glob edge cases:** 0 matches → exit code 2 + error message; 1 match → exit 0 (trivially compatible).
- **Exit codes:** 0 = compatible, 1 = incompatible, 2 = usage error, 3 = runtime error (IO/parse failure).
- **stderr metadata:** Both modes print file comparison info to stderr. New `--quiet` / `-q` flag suppresses it. Compatibility report (issues) stays on stdout.
- **Output on success:** stdout silent on compatible schemas in TEXT mode. `--output JSON` always produces valid JSON (`[]` on compatible, issue array on incompatible).
- **Version and docs:** Bump version (breaking change due to arg order reversal). Update README to document both modes, new arg order, exit codes, `--quiet` flag.
- **Override picocli default error exit code** to guarantee 2 for usage errors.
- **Document exit codes** in both `--help` footer and README.

### Claude's Discretion

- Natural sort implementation details
- Exact stderr metadata format
- How to structure the `--help` exit codes footer
- Internal refactoring needed to support both modes

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CLI-01 | chuckd uses typed exit codes (0=compatible, 1=incompatible, 2=usage error) instead of returning issues count | picocli `@Command` exit code annotations and `IExitCodeExceptionMapper` make this straightforward; the fix to `call()` returning `issues.size()` is the core change |
| CLI-02 | chuckd accepts a glob pattern and finds/sorts matching files, treating the last match as latest schema | Java NIO `PathMatcher` + `Files.walk` provides glob expansion; natural sort requires a custom Comparator; mode dispatch (1 arg vs 2+) controls which path executes |
</phase_requirements>

---

## Summary

Phase 1 modifies the existing `ChuckD.java` CLI in two focused ways. First, it normalises exit codes: the current `call()` returns `issues.size()`, which wraps to 0 when there are exactly 256 incompatibilities (a silent false-pass). The fix is to return typed codes (0/1/2/3) using picocli's built-in `@Command` exit code annotations and `IExitCodeExceptionMapper`. Second, it adds glob mode so a user can pass a single quoted glob pattern instead of listing every file; Java's standard `PathMatcher` + `Files.walk` handles expansion, and a custom natural-sort Comparator ensures numeric version segments order correctly.

The work is contained entirely within `ChuckD.java`, `smoke.bats`, and `README.md`. No new dependencies are required — everything needed (picocli's exit code machinery, Java NIO PathMatcher) already exists in the project. The only structural question is how to cleanly express the "1 arg = glob mode, 2+ args = explicit mode" dispatch inside picocli's `@Parameters` model; the recommended pattern is a single `List<String>` parameter with `arity = "1..*"` and runtime inspection of the list size inside `call()`.

**Primary recommendation:** Implement all changes in `ChuckD.java` as a single coherent wave — fix exit codes first (they are independent of arg restructuring), then reverse arg order and add glob expansion, then add stderr metadata and `--quiet`, then update tests and docs.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| picocli | 4.7.7 | CLI parsing, help, exit codes | Already in project; version confirmed in `app/build.gradle` |
| Java NIO (`java.nio.file`) | JDK 21 | PathMatcher glob expansion, Files.walk | Part of JDK, no new dependency needed |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| BATS (bats-core) | any | Smoke-test the native binary | Already in project via `bat-tests/smoke.bats` |
| JUnit Jupiter | 6.0.3 | Unit tests for ChuckD and helpers | Already used for existing `ChuckDTest*` tests |
| Gradle semver plugin | 1.0.4 | Bump version.properties | Already wired: `gradle incrementMinor` or `gradle -Dversion.semver=1.0.0` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Java NIO PathMatcher | Apache Commons IO or external glob library | No benefit — PathMatcher handles `*`, `**`, `?`, and `{a,b}` syntax natively since Java 7 |
| Custom natural sort | Apache Commons Lang `AlphaNumericComparator` | `commons-lang3` isn't in the project; a 20-line comparator is simpler than adding a dependency |
| `IExitCodeExceptionMapper` | Wrapping `execute()` in try/catch | `IExitCodeExceptionMapper` is the idiomatic picocli way and handles picocli-internal `ParameterException` cleanly |

**Installation:** No new dependencies required.

---

## Architecture Patterns

### Recommended Project Structure

No new source files needed. All changes are within existing files:

```
app/src/main/java/com/anentropic/chuckd/
└── ChuckD.java          # all CLI changes live here

bat-tests/
└── smoke.bats           # update exit code assertions and arg order

README.md                # update usage, arg order, exit codes, --quiet
app/src/main/resources/
└── version.properties   # bumped by gradle task
```

### Pattern 1: Single List Parameter for Dual-Mode Dispatch

**What:** Replace the two separate `@Parameters` fields with a single `List<String>` accepting `arity = "1..*"`. Inside `call()`, inspect the list size: 1 = glob mode, 2+ = explicit mode (last element is new schema, rest are previous).

**When to use:** When the same positional slot must behave differently based on count, and you want picocli to handle the minimum-arity validation (at least 1 arg required) for free.

**Example:**
```java
// Replaces the two @Parameters fields (index=0 and index=1)
@Parameters(
    arity = "1..*",
    description = "Glob mode (1 arg): pass a quoted glob pattern, e.g. \"schemas/person.*.json\"\n" +
                  "Explicit mode (2+ args): <previous...> <new> — last arg is the new schema"
)
List<String> schemaArgs;

@Override
public Integer call() throws Exception {
    configureRootLogger();
    List<Path> schemaPaths;
    if (schemaArgs.size() == 1) {
        schemaPaths = expandGlob(schemaArgs.get(0));  // returns naturally-sorted paths
        if (schemaPaths.isEmpty()) {
            System.err.println("No files matched pattern: " + schemaArgs.get(0));
            return 2;
        }
        if (schemaPaths.size() == 1) {
            if (!quiet) System.err.println("Single match, trivially compatible: " + schemaPaths.get(0));
            return 0;
        }
    } else {
        schemaPaths = schemaArgs.stream().map(Path::of).collect(Collectors.toList());
    }
    // Last path = new schema; all prior = previous schemas (in order)
    Path newSchemaPath = schemaPaths.get(schemaPaths.size() - 1);
    List<Path> previousPaths = schemaPaths.subList(0, schemaPaths.size() - 1);
    // ... load, compare, return 0 or 1
}
```

### Pattern 2: picocli Exit Code Declaration

**What:** Add `exitCodeOnInvalidInput` and `exitCodeOnExecutionException` to the `@Command` annotation, and use `IExitCodeExceptionMapper` on the `CommandLine` instance in `main()` to map runtime exceptions (IO, parse) to exit code 3.

**When to use:** Whenever `call()` returns typed codes and you need picocli's own error paths (bad flags, missing args) to also emit the correct typed code.

**Example:**
```java
// Source: https://picocli.info/apidocs/picocli/CommandLine.Command.html
@Command(
    name = "chuckd",
    mixinStandardHelpOptions = true,
    exitCodeOnInvalidInput = 2,        // picocli parse errors -> exit 2
    exitCodeOnExecutionException = 3,  // unhandled exceptions -> exit 3
    description = "Report evolution compatibility of latest vs existing schema versions.",
    versionProvider = VersionProvider.class,
    footer = {
        "",
        "Exit codes:",
        "  0   Compatible (or trivially compatible with a single glob match)",
        "  1   Incompatible — breaking changes detected",
        "  2   Usage error — bad arguments, missing files, or glob matches nothing",
        "  3   Runtime error — file I/O failure or schema parse error"
    }
)
class ChuckD implements Callable<Integer> { ... }

// In main():
public static void main(String... args) {
    DateTimeZone.setProvider(new UTCProvider());
    int exitCode = new CommandLine(new ChuckD())
        .setExitCodeExceptionMapper(t -> {
            if (t instanceof CommandLine.ParameterException) return 2;
            return 3;  // IOException, schema parse error, etc.
        })
        .execute(args);
    System.exit(exitCode);
}
```

**Key insight:** picocli already defaults `exitCodeOnInvalidInput` to 2, but declaring it explicitly in the annotation makes the intent clear and ensures it stays 2 even if picocli changes its default in a future version.

### Pattern 3: Java NIO PathMatcher Glob Expansion

**What:** Resolve the glob pattern against the filesystem using `FileSystems.getDefault().getPathMatcher("glob:" + pattern)`, then walk from the pattern's parent directory.

**When to use:** Single-argument glob mode.

**Example:**
```java
// Source: java.nio.file JavaDoc — https://docs.oracle.com/en/java/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)
private List<Path> expandGlob(String pattern) throws IOException {
    Path patternPath = Path.of(pattern);
    // Resolve search root: use the pattern's parent, or CWD if no parent component
    Path searchRoot = patternPath.getParent() != null ? patternPath.getParent() : Path.of(".");
    String globPattern = "glob:" + patternPath.getFileName().toString();
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

    try (Stream<Path> stream = Files.walk(searchRoot, 1)) {
        return stream
            .filter(p -> !Files.isDirectory(p))
            .filter(p -> matcher.matches(p.getFileName()))
            .sorted(NaturalSortComparator.INSTANCE)
            .collect(Collectors.toList());
    }
}
```

**Note on depth:** `Files.walk(root, 1)` limits to direct children. Glob patterns with `**` would need `Integer.MAX_VALUE`. The locked decision's example (`"schemas/person.*.json"`) uses a flat directory pattern, so depth 1 is the right default. Use `Files.walk(searchRoot)` (no depth limit) if `**` is present in the pattern.

### Pattern 4: Natural Sort Comparator

**What:** Compare path strings by splitting on numeric/non-numeric boundaries and comparing chunks — numeric chunks as integers, others lexicographically.

**When to use:** Sorting glob results so `v8, v9, v10` order correctly.

**Example:**
```java
// No external library needed — ~25 lines
static int naturalCompare(String a, String b) {
    int i = 0, j = 0;
    while (i < a.length() && j < b.length()) {
        char ca = a.charAt(i), cb = b.charAt(j);
        if (Character.isDigit(ca) && Character.isDigit(cb)) {
            // consume digit runs
            int start_i = i, start_j = j;
            while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
            while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
            int na = Integer.parseInt(a.substring(start_i, i));
            int nb = Integer.parseInt(b.substring(start_j, j));
            if (na != nb) return Integer.compare(na, nb);
        } else {
            if (ca != cb) return Character.compare(ca, cb);
            i++; j++;
        }
    }
    return a.length() - b.length();
}

// Use as: Comparator.comparing(p -> p.getFileName().toString(), NaturalSortComparator::naturalCompare)
```

### Pattern 5: stderr Metadata Output

**What:** Print diagnostic lines (which files were matched, which is "new" vs "previous") to `System.err` rather than `System.out`. Suppressed when `--quiet` / `-q` is active.

**When to use:** Both explicit and glob modes.

**Example:**
```java
@Option(names = {"-q", "--quiet"},
        description = "Suppress file metadata output on stderr"
) boolean quiet;

// Before comparison:
if (!quiet) {
    System.err.println("Previous: " + previousPaths.stream()
        .map(Path::toString).collect(Collectors.joining(", ")));
    System.err.println("New:      " + newSchemaPath);
}
```

### Anti-Patterns to Avoid

- **Returning `issues.size()` as exit code:** The whole reason for this phase — 256 issues wraps to exit 0. Never return a count as an exit code.
- **Shell glob expansion without quoting guidance:** The glob is intentionally quoted in the user's shell call. If users pass an unquoted glob, the shell expands it and chuckd sees 2+ args (explicit mode). This is the designed "both modes coexist" property — document it, don't try to detect glob characters in explicit mode.
- **Calling `System.exit()` inside `call()`:** Return typed integers from `call()` and let `CommandLine.execute()` call `System.exit()`. The existing `main()` pattern already handles this correctly.
- **Walking the entire filesystem:** Always anchor the glob walk to the pattern's parent directory. A bare filename glob like `*.json` should walk `.`, not `/`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Glob file matching | Custom regex-based file scanner | `java.nio.file.PathMatcher` with `"glob:"` syntax | Built into JDK; handles `*`, `**`, `?`, `{a,b}` correctly across OS path separators |
| Usage error exit code | Custom exception handlers | `@Command(exitCodeOnInvalidInput = 2)` + picocli default `ParameterException` handling | picocli already catches bad args and routes through `exitCodeOnInvalidInput`; no manual catching needed |
| Version bump | Manual file editing | `gradle incrementMinor` (net.thauvin.erik.gradle.semver plugin) | Already wired in `app/build.gradle`; updates `version.properties` atomically |

**Key insight:** All the infrastructure already exists in the project. This phase is about wiring it together correctly, not introducing new frameworks.

---

## Common Pitfalls

### Pitfall 1: The 256-Wrap Bug (the whole reason for this phase)
**What goes wrong:** `call()` returns `issues.size()`. Java `System.exit()` takes an `int` but the OS treats it as a single byte. Exactly 256 issues → exit code 0 → CI passes silently.
**Why it happens:** Treating a count as an exit code conflates "how bad is it" with "did it fail".
**How to avoid:** `call()` must return `0` (empty issues list) or `1` (non-empty issues list). Never the count.
**Warning signs:** Any test asserting `$status -gt 0` passes but a specific `$status -eq 1` test fails.

### Pitfall 2: Arg Order Reversal Breaks Existing Tests
**What goes wrong:** All existing `ChuckDTest*` tests and `smoke.bats` pass args in old order (`new` first, `previous...` second). After the reversal, every test that passes 2+ files will test the wrong schema as "new".
**Why it happens:** The arg order is a breaking change — picocli parses positionally, so `index=0` changes meaning.
**How to avoid:** Update all test call sites in the same task as the arg order change. The `getReport()` helper in `ChuckDTestBase` assembles args — fix it there once, then all subclass tests inherit the fix.
**Warning signs:** Tests pass with compatible schemas even when the new schema should be incompatible.

### Pitfall 3: PathMatcher Requires Absolute or Relative-Consistent Paths
**What goes wrong:** `matcher.matches(path)` returns false when `path` is absolute but the pattern was built from a relative component, or vice versa.
**Why it happens:** PathMatcher in glob mode matches the full path string, not just the filename. A pattern built as `glob:*.json` only matches a filename segment, not `/full/path/to/file.json`.
**How to avoid:** Always call `matcher.matches(p.getFileName())` (not the full path) when the pattern contains no directory component. If the pattern has a parent component, split it: the parent becomes the walk root, the filename-only portion becomes the glob pattern.
**Warning signs:** `expandGlob("schemas/person.*.json")` returns 0 results even though the files exist.

### Pitfall 4: GraalVM Native Image and PathMatcher
**What goes wrong:** `FileSystems.getDefault()` and `PathMatcher` are standard JDK APIs with no reflection; they work in native image without configuration.
**Why it matters:** This is actually *not* a pitfall — no `reflection.json` changes are needed for glob expansion. The existing `reflection.json` only covers schema library classes.
**Confidence:** HIGH — Java NIO is fully supported in GraalVM native image without metadata.

### Pitfall 5: picocli `ParameterException` vs Runtime Exceptions
**What goes wrong:** Setting `exitCodeOnExecutionException = 3` on the `@Command` catches all `Exception` thrown from `call()`. But `ParameterException` (bad flag values, wrong arg count) is a picocli-internal exception thrown *before* `call()` runs — it routes through `exitCodeOnInvalidInput` instead. These two paths are separate in picocli.
**How to avoid:** Understand the picocli execution flow: parse errors → `exitCodeOnInvalidInput` (default 2); exceptions from `call()` → `exitCodeOnExecutionException` (or `IExitCodeExceptionMapper` if set). For exit code 3 (runtime errors like `IOException`), throw from `call()` and picocli will pick up `exitCodeOnExecutionException = 3`.
**Warning signs:** File-not-found errors produce exit code 1 (picocli's default `exitCodeOnExecutionException`) instead of 3.

### Pitfall 6: `--output JSON` Must Always Produce Valid JSON
**What goes wrong:** In success path, TEXT mode prints nothing (correct), but JSON mode currently prints nothing too. A downstream consumer expecting `[]` on success gets empty output instead.
**Why it happens:** The current `call()` only prints when `!issues.isEmpty()`.
**How to avoid:** After the fix, JSON mode must always call `formatJson(issues)` even when `issues` is empty, producing `[]`.
**Warning signs:** `chuckd --output JSON` on compatible schemas produces no stdout output instead of `[]`.

---

## Code Examples

Verified patterns from official sources and code inspection:

### Current `call()` (the bug)
```java
// From ChuckD.java line 178 — the false-0 risk
return issues.size();  // WRONG: 256 issues → exit 0
```

### Fixed `call()` return (exit code logic only)
```java
// Return typed exit codes — JSON mode always produces output
if (outputFormat == OutputFormat.JSON) {
    System.out.println(formatJson(issues));  // always: [] or [...]
} else if (!issues.isEmpty()) {
    System.out.print(formatText(issues));
}
return issues.isEmpty() ? 0 : 1;
```

### picocli exit code annotation (source: picocli 4.7.7 API docs)
```java
// Source: https://picocli.info/apidocs/picocli/CommandLine.Command.html
@Command(
    name = "chuckd",
    mixinStandardHelpOptions = true,
    exitCodeOnInvalidInput = 2,
    exitCodeOnExecutionException = 3,
    footer = {
        "",
        "Exit codes:",
        "  0   Compatible",
        "  1   Incompatible",
        "  2   Usage error",
        "  3   Runtime error"
    },
    ...
)
```

### BATS test pattern for typed exit codes
```bash
# Old pattern (too loose):
[ "$status" -gt 0 ]

# New patterns (typed):
@test "incompatible schemas -> exit 1" {
  run "${bin_path}/chuckd" -c FORWARD "${res_path}/jsonschema/person-base.json" "${res_path}/jsonschema/person-narrowed.json"
  [ "$status" -eq 1 ]
}

@test "glob mode, no matches -> exit 2" {
  run "${bin_path}/chuckd" "nonexistent/*.json"
  [ "$status" -eq 2 ]
}

@test "glob mode, single match -> exit 0" {
  run "${bin_path}/chuckd" "${res_path}/jsonschema/person-base.json"
  [ "$status" -eq 0 ]
}
```

**Note on arg order in updated smoke.bats:** After the reversal, explicit mode args are `<previous...> <new>`, so the existing two-file tests become `chuckd [opts] <older-file> <newer-file>` — the incompatibility direction assertions may need checking since which file is "new" flips.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `return issues.size()` as exit code | `return issues.isEmpty() ? 0 : 1` | Phase 1 | Fixes silent false-pass at 256 issues |
| `<new> <previous...>` positional order | `<previous...> <new>` positional order | Phase 1 | Enables shell glob expansion to work naturally; breaking change |
| No glob support | Single-arg glob mode via PathMatcher | Phase 1 | Users don't have to enumerate every schema version |
| No stderr metadata | Stderr metadata + `--quiet` flag | Phase 1 | CI logs show which files were compared |

**Deprecated/outdated after this phase:**
- `index=0` as `newSchemaFile`, `index=1..*` as `previousSchemaFiles`: replaced by unified `List<String> schemaArgs` with runtime dispatch
- `File[]` type for previous schemas: replaced by `List<String>` (paths resolved to `Path` objects inside `call()`)

---

## Open Questions

1. **Glob pattern with directory separator in filename portion**
   - What we know: The example in CONTEXT.md is `"schemas/person.*.json"` — a flat directory with glob in the filename only.
   - What's unclear: Should `**` (recursive) glob patterns be supported? E.g., `"schemas/**/person.*.json"`.
   - Recommendation: Implement depth-1 walk for now (matching CONTEXT.md examples). `**` can be added later by detecting its presence and switching to `Files.walk(root)` without a depth limit.

2. **Glob pattern with no directory component (bare `"*.json"`)**
   - What we know: `Path.of("*.json").getParent()` returns `null` — must default walk root to CWD (`.`).
   - What's unclear: Does the user expect CWD-relative resolution?
   - Recommendation: Yes — fall back to `Path.of(".")` when parent is null. This matches shell glob expansion semantics.

3. **Natural sort integer overflow**
   - What we know: Version numbers in schema filenames are small integers.
   - What's unclear: Could a numeric chunk exceed `Integer.MAX_VALUE`?
   - Recommendation: Use `Long.parseLong` instead of `Integer.parseInt` in the natural sort comparator to be safe. The performance cost is negligible.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.0.3 (unit tests) + BATS (smoke tests on native binary) |
| Config file | `app/build.gradle` — `test { useJUnitPlatform() }` |
| Quick run command | `cd /Users/paul/Documents/Dev/Personal/chuckd && ./gradlew :app:test` |
| Full suite command | `./gradlew :app:test && bats bat-tests/smoke.bats` (requires native binary) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CLI-01 | Compatible schemas exit 0 | smoke | `bats bat-tests/smoke.bats` | ✅ (update existing test) |
| CLI-01 | Incompatible schemas exit 1 (not issues.size()) | smoke | `bats bat-tests/smoke.bats` | ✅ (update `[ $status -gt 0 ]` → `[ $status -eq 1 ]`) |
| CLI-01 | Bad args exit 2 | smoke | `bats bat-tests/smoke.bats` | ❌ Wave 0 — new test |
| CLI-01 | File-not-found exits 3 | smoke | `bats bat-tests/smoke.bats` | ❌ Wave 0 — new test |
| CLI-01 | `--output JSON` produces `[]` on success | unit | `./gradlew :app:test` | ❌ Wave 0 — new unit test in `ChuckDTestJSONSchema` |
| CLI-02 | Glob with 2+ matches: last is new schema, rest are previous | unit | `./gradlew :app:test` | ❌ Wave 0 — new unit test for `expandGlob()` |
| CLI-02 | Glob with 0 matches: exits 2 | smoke | `bats bat-tests/smoke.bats` | ❌ Wave 0 — new test |
| CLI-02 | Glob with 1 match: exits 0 | smoke | `bats bat-tests/smoke.bats` | ❌ Wave 0 — new test |
| CLI-02 | Natural sort orders `v8, v9, v10` correctly | unit | `./gradlew :app:test` | ❌ Wave 0 — unit test for NaturalSort comparator |
| CLI-02 | Reversed arg order: last arg is new schema in explicit mode | unit | `./gradlew :app:test` | ✅ (update existing `ChuckDTest*` tests — currently use old order) |

### Sampling Rate
- **Per task commit:** `./gradlew :app:test`
- **Per wave merge:** `./gradlew :app:test && bats bat-tests/smoke.bats`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] New BATS tests in `bat-tests/smoke.bats` — covers CLI-01 (exit 2, exit 3) and CLI-02 (glob 0 matches, glob 1 match)
- [ ] New unit test `ChuckDTestGlob.java` or method in existing test class — covers `expandGlob()` logic and natural sort
- [ ] Update existing `ChuckDTest*` test arg order — all current tests pass `new` as first arg; after reversal they must pass `new` as last arg
- [ ] BATS test updates: change `[ "$status" -gt 0 ]` to `[ "$status" -eq 1 ]` and reverse file order in existing test invocations

*(No new framework install needed — JUnit and BATS are already available)*

---

## Sources

### Primary (HIGH confidence)
- picocli 4.7.7 API — `CommandLine.Command` annotation: https://picocli.info/apidocs/picocli/CommandLine.Command.html — `exitCodeOnInvalidInput`, `exitCodeOnExecutionException`, `footer` attributes
- picocli 4.7.7 API — `IExitCodeExceptionMapper`: https://picocli.info/apidocs/picocli/CommandLine.IExitCodeExceptionMapper.html
- picocli 4.7.7 API — `ExitCode` constants: https://picocli.info/apidocs/picocli/CommandLine.ExitCode.html
- `ChuckD.java` (direct code inspection) — current `@Parameters`, `call()` implementation, `main()` structure
- `app/build.gradle` (direct inspection) — picocli 4.7.7, JUnit Jupiter 6.0.3, semver plugin, GraalVM native config
- Java SE 21 API — `java.nio.file.PathMatcher`, `FileSystem.getPathMatcher()`, `Files.walk()` — built-in, no configuration needed

### Secondary (MEDIUM confidence)
- picocli Quick Guide: https://picocli.info/quick-guide.html — exit code with `Callable` and `execute()`
- Java I/O Tutorial (Oracle): https://docs.oracle.com/javase/tutorial/essential/io/find.html — PathMatcher glob syntax and walk pattern

### Tertiary (LOW confidence)
- None — all critical claims verified against picocli official API docs and direct code inspection

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — picocli version and APIs verified against official 4.7.7 API docs; Java NIO is standard JDK
- Architecture: HIGH — patterns derived from direct code inspection of `ChuckD.java` combined with verified picocli APIs
- Pitfalls: HIGH — exit code wrap bug is directly observable in `call()` line 178; arg order issue follows from the change; PathMatcher behaviour verified against official docs

**Research date:** 2026-03-08
**Valid until:** 2026-09-08 (picocli and Java NIO are very stable APIs)
