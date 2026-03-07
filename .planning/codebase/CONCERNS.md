# Codebase Concerns

**Analysis Date:** 2026-03-07

## Tech Debt

**Raw exception handling in ChuckD main logic:**
- Issue: `e.printStackTrace()` called directly with `System.exit(1)` instead of proper error handling
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:126`
- Impact: Stack trace printed to stderr is not user-friendly; no structured error reporting possible in machine-readable contexts
- Fix approach: Wrap reflection exceptions in a custom exception, handle gracefully in `call()` method, return structured error via `formatJson()` or text output. Avoid direct `System.exit()` in `loadSchemas()` — let picocli handle exit codes.

**GraalVM reflection configuration is overly broad:**
- Issue: All schema provider classes use `"allDeclaredMethods": true` and `"allPublicMethods": true` which includes methods not needed at runtime
- Files: `app/src/main/resources/reflection.json`
- Impact: Larger native image binary size; potential security surface; harder to audit what's actually reflectively instantiated
- Fix approach: Audit actual reflection usage in Confluent libraries and narrow configuration to only required methods. Document why each class requires reflection.

**No input validation on schema files:**
- Issue: No validation that provided files are actually readable schemas; errors only surface during parsing
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:114-135`
- Impact: Missing files result in unclear error messages buried in exception traces
- Fix approach: Add pre-flight checks in `loadSchemas()`: verify files exist and are readable before attempting to parse. Provide clear error messages for file-not-found and permission-denied cases.

## Known Bugs

**Joda-Time timezone initialization workaround:**
- Symptoms: "Resource not found: org/joda/time/tz/data/ZoneInfoMap" errors in native image
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:182-185`
- Trigger: Used when Confluent schema libraries internally invoke joda-time
- Workaround: Current code sets `DateTimeZone.setProvider(new UTCProvider())` — this hardcodes UTC behavior
- Impact: Any schema validation that depends on timezone-specific behavior will use UTC instead of local/specified timezone. Low impact for schema compatibility checking, but worth documenting.

## Error Handling Gaps

**Missing error handler for empty previous schemas list:**
- Issue: Code assumes at least one previous schema exists (`.get(previousSchemas.size() - 1)` in `CompatibilityReporter.comparePair()`)
- Files: `app/src/main/java/com/anentropic/chuckd/CompatibilityReporter.java:35`
- Risk: IndexOutOfBoundsException if called with empty list. User-provided empty args would trigger this.
- Safe fix: Add validation in `ChuckD.loadSchemas()` that ensures at least one previous schema file is provided (picocli already enforces `arity = "1..*"` but document it).

**No handling for malformed schema format specification:**
- Issue: If user provides invalid `--format` value, picocli will catch it, but there's no fallback logging
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:66-70`
- Risk: Error message depends entirely on picocli's enum parsing output
- Safe fix: Current picocli configuration is acceptable but consider adding a custom exception handler in `CommandLine` for better UX.

**JSON output formatting could throw if SchemaIncompatibility has null fields:**
- Issue: `formatJson()` uses ObjectMapper with default pretty printer; null fields are included
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:162-165`
- Risk: Low — Jackson handles nulls fine, but no validation that required fields are non-null
- Fix approach: Ensure `SchemaIncompatibility` record is constructed with non-null type/path in all code paths. Currently the record allows nulls for `message` field intentionally (Protobuf/JSON Schema don't include messages), which is correct.

## Dependency Risks

**Confluent Schema Registry 7.8.0 — heavy transitive dependency tree:**
- Risk: Brings in 40+ transitive dependencies including Kafka, Avro, Protobuf ecosystems
- Impact: Large JAR size (mitigated by GraalVM native-image), long build times, potential compatibility issues with other tools in same environment
- Mitigation in place: `shadowJar` task bundles everything; Docker image uses `scratch` base to avoid OS-level conflicts
- Note: This is an unavoidable trade-off for supporting multiple schema formats. Consider if project should ever split into separate tools per-format.

**Joda-Time is deprecated in Java ecosystem:**
- Risk: Confluent libraries use joda-time; project now has to work around timezone initialization
- Impact: Potential incompatibility with future Confluent versions that drop joda-time
- Monitoring: Check Confluent Schema Registry release notes for joda-time removal plans. May require `reflection.json` updates or code changes.

## Security Considerations

**Command-line input not escaped in error output:**
- Risk: If a schema file path or content triggers an exception with unsanitized output, special characters could appear in error messages
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:176`, `CompatibilityReporter.java`
- Current mitigation: Exception handling doesn't echo back user input; picocli handles argument parsing safely
- Recommendation: Low risk in CLI context, but if JSON output is ever consumed by another system, audit for injection vectors.

**Reflection configuration in GraalVM native image:**
- Risk: Overly broad reflection allowlist could enable unexpected code paths in native image
- Files: `app/src/main/resources/reflection.json`
- Current state: All schema providers and log4j classes are reflectively accessible
- Recommendation: Narrow to specific methods actually used by Confluent at runtime. Consider using GraalVM's agent to generate minimal configuration.

**No input size limits on schema files:**
- Risk: No maximum file size validation; extremely large schema files could cause memory exhaustion or DoS
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:116`
- Impact: Low for typical usage, but important if used in automated CI pipelines with untrusted inputs
- Recommendation: Add reasonable file size check (e.g., reject schemas > 100MB). Document limitation.

## Fragile Areas

**CompatibilityReporter logic depends on format-specific API contracts:**
- Files: `app/src/main/java/com/anentropic/chuckd/CompatibilityReporter.java:56-70`
- Why fragile: Each format (JSON Schema, Avro, Protobuf) has different comparison APIs with different result types:
  - Avro: Uses Apache Avro's `SchemaCompatibility.checkReaderWriterCompatibility()`
  - JSON Schema: Uses Confluent's `SchemaDiff.compare()` with `Difference.Type` enum
  - Protobuf: Uses Confluent's `SchemaDiff.compare()` with different `Difference.Type` enum
- Issue: If Confluent updates any of these APIs, code breaks silently without proper type checking
- Safe modification: Add integration tests for each format that verify the exact structure of returned incompatibilities. Include version expectations for Confluent and Apache Avro libraries.
- Test coverage: `CompatibilityReporterTest.java` has format-specific tests but doesn't verify against multiple versions of upstream libraries.

**Transitive dependency on GraalVM build artifacts:**
- Files: `Dockerfile`, `app/build.gradle` (graalvmNative plugin)
- Why fragile: GraalVM native-image command changes behavior between versions; reflection configuration syntax could evolve
- Risk: Minor version bumps of GraalVM (e.g., 21.0.1 → 21.0.2) might introduce incompatible options like `--enable-url-protocols=https`
- Monitoring: Subscribe to GraalVM release notes; test against new versions in weekly build before major updates.

**OutputFormat enum controls JSON vs TEXT output with no extensibility:**
- Files: `app/src/main/java/com/anentropic/chuckd/ChuckD.java:54-57`
- Issue: Adding new output formats (e.g., CSV, SARIF, XML) requires modifying switch statement in `call()`
- Safe modification: Add an OutputFormatter interface with implementations, use reflection or registry to avoid switch statement bloat.

## Scaling Limits

**Single-threaded schema comparison:**
- Current capacity: Processes schemas sequentially; for transitive checks, compares N previous versions one-at-a-time
- Limit: With 100+ schema versions and FULL_TRANSITIVE mode, user waits for all pairwise comparisons
- Scaling path: Parallelize transitive checks using CompletableFuture or parallel stream. Likely low impact since schema parsing is typically fast, but worth considering for large schema histories.

**No memory profiling for native image:**
- Risk: Peak memory usage not measured or documented; potential issues on memory-constrained systems
- Impact: Docker/CI environments with strict memory limits might OOM unexpectedly
- Recommendation: Run memory profiling on largest test schemas; document heap recommendations in README.

## Test Coverage Gaps

**CLI argument parsing not fully tested:**
- Untested areas:
  - Invalid compatibility level values (picocli catches, but error handling not tested)
  - Missing required arguments (picocli catches, but error path not tested)
  - Large number of previous schemas (no performance test)
  - Non-existent schema files (error message quality not verified)
- Files: `app/src/test/java/com/anentropic/chuckd/ChuckDTest*.java`
- Risk: User-facing error messages could be unhelpful or misleading
- Priority: Medium — users will encounter these quickly if they make mistakes

**Native image smoke tests are minimal:**
- Untested areas:
  - Complex schema structures (deeply nested JSON Schema, complex Protobuf messages)
  - Edge cases: empty schemas, schemas with special characters in field names
  - Error scenarios: malformed input, file permission errors
- Files: `bat-tests/smoke.bats`
- Risk: Subtle bugs in native image compilation only surface in production
- Priority: Medium — add test cases for error paths and complex schemas to `smoke.bats`

**No integration tests with actual Confluent Schema Registry:**
- Untested area: Whether compatibility results match actual CSR behavior when schemas are registered
- Risk: Subtle differences in incompatibility detection compared to CSR
- Recommendation: Add optional integration tests (marked as slow/optional) that spin up a CSR instance and compare results. Lower priority since parity tests in `CompatibilityReporterTest` already exist.

**Docker image runtime not tested:**
- Untested: Actual Docker runtime behavior (e.g., volume mounting, exit codes, stderr handling)
- Risk: Works in native-image tests but fails when wrapped in Docker
- Fix: Add a simple Docker smoke test in CI that mounts schemas and verifies output

**Version mismatch scenarios not tested:**
- Untested: What happens when schema files reference different versions of schema format specs
- Risk: Silent failures or incorrect compatibility results if formats are mixed
- Recommendation: Add tests for schemas from different JSON Schema draft versions, different Avro specification versions, etc.

## Missing Features

**No support for schema composition / references:**
- Problem: Some JSON Schemas use `$ref` to reference external schemas, which are not validated
- Impact: References are resolved at validation time but source location is not validated upfront
- Blocks: Users with highly modular schema designs can't easily validate all interdependencies
- Priority: Low — most users have flat schema files. Defer unless customer demand appears.

**No recursive directory scanning:**
- Problem: Must provide explicit file paths; can't validate all schemas in a directory
- Impact: CI/CD pipelines must generate file lists manually
- Blocks: Convenient validation of monorepo schema directories
- Priority: Low — shell globbing or CI tooling can work around this. Document in README.

---

*Concerns audit: 2026-03-07*
