# Architecture

**Analysis Date:** 2026-03-07

## Pattern Overview

**Overall:** Single-purpose CLI application with pluggable schema format handlers

**Key Characteristics:**
- Command-line driven via PicoCLI framework with typed options
- Pluggable schema provider pattern supporting three format types (JSON Schema, Avro, Protobuf)
- Delegated compatibility checking to Confluent Schema Registry implementations
- Fail-fast validation with structured error reporting
- Native image compilation via GraalVM

## Layers

**CLI & Command Handling:**
- Purpose: Parse arguments, define application interface, coordinate execution flow
- Location: `app/src/main/java/com/anentropic/chuckd/ChuckD.java`
- Contains: PicoCLI command class with argument options and parameters
- Depends on: Schema loading, reporting, version provider, Confluent schema providers
- Used by: Main entry point, test harnesses

**Schema Loading & Parsing:**
- Purpose: Load schema files and instantiate appropriate format-specific providers
- Location: `app/src/main/java/com/anentropic/chuckd/ChuckD.java` (loadSchemas, loadSchema methods)
- Contains: Provider instantiation logic, file reading, schema parsing
- Depends on: Confluent schema provider classes, file system
- Used by: CLI layer before compatibility checking

**Compatibility Checking:**
- Purpose: Compare schemas and identify incompatibilities according to specified rules
- Location: `app/src/main/java/com/anentropic/chuckd/CompatibilityReporter.java`
- Contains: Format-specific comparison implementations, transitive/non-transitive logic
- Depends on: Confluent schema implementations, parsed schema objects
- Used by: CLI layer for generating reports

**Data Model:**
- Purpose: Represent schema incompatibilities in structured format
- Location: `app/src/main/java/com/anentropic/chuckd/SchemaIncompatibility.java`
- Contains: Record class with type, path, direction, message fields
- Depends on: Nothing (standalone data class)
- Used by: CompatibilityReporter, output formatting

**Version Management:**
- Purpose: Provide application version from build-time generated properties
- Location: `app/src/main/java/com/anentropic/chuckd/VersionProvider.java`
- Contains: PicoCLI version provider implementation, properties file loading
- Depends on: version.properties resource file
- Used by: CLI layer via PicoCLI annotations

## Data Flow

**Schema Compatibility Check (Happy Path):**

1. User invokes `chuckd <new-schema> <old-schema> [<older-schemas>...]` with optional flags
2. PicoCLI (via `main()`) parses arguments into ChuckD instance fields
3. `ChuckD.call()` executes:
   - `configureRootLogger()` sets up logging from flag
   - `getStructuredReport()` invokes validation
4. `getStructuredReport()` calls:
   - `loadSchemas()` instantiates appropriate provider from `formatToProvider` map and loads all files
   - `CompatibilityReporter.check()` compares schemas according to compatibility level
5. `check()` logic:
   - Determines comparison mode: transitive checks all previous schemas (reversed), non-transitive checks only most recent
   - For each schema: compares based on compatibility mode (backward/forward/full)
   - Delegates format-specific comparison: `compareJsonSchemas()`, `compareAvroSchemas()`, or `compareProtobufSchemas()`
   - Returns on first incompatibility (fail-fast) or empty list if compatible
6. Back in `call()`, if issues exist, format output as TEXT or JSON
7. Return exit code matching issue count (0 = compatible, >0 = incompatible)

**State Management:**

- ChuckD instance holds parsed arguments as fields
- Schema objects (ParsedSchema, AvroSchema, etc.) are immutable from Confluent registry
- Compatibility check state is transient within `check()` method
- No persistent state between invocations

## Key Abstractions

**Schema Format Provider:**
- Purpose: Abstract schema parsing and compatibility checking across different formats
- Examples: `JsonSchemaProvider`, `AvroSchemaProvider`, `ProtobufSchemaProvider` from Confluent
- Pattern: Strategy pattern with runtime provider selection via `formatToProvider` map

**Parsed Schema:**
- Purpose: Represent a loaded schema regardless of underlying format
- Examples: `JsonSchema`, `AvroSchema`, `ProtobufSchema` (all implement `ParsedSchema`)
- Pattern: Polymorphic interface allows format-agnostic schema storage

**Compatibility Comparison:**
- Purpose: Compare two schemas and return incompatibilities
- Examples: `SchemaDiff.compare()` (JSON Schema & Protobuf), `SchemaCompatibility.checkReaderWriterCompatibility()` (Avro)
- Pattern: Each format has its own diff algorithm; chuckd delegates to Confluent implementations

**Incompatibility Report:**
- Purpose: Structure compatibility check results for unified output
- Examples: `SchemaIncompatibility` record, aggregated in List for output
- Pattern: Value object for serialization to JSON or text formatting

## Entry Points

**Main CLI Entry:**
- Location: `app/src/main/java/com/anentropic/chuckd/ChuckD.java` (main method, lines 181-189)
- Triggers: User invocation from shell or Docker
- Responsibilities: Initialize DateTimeZone provider (for GraalVM native image), instantiate ChuckD command, execute via PicoCLI, exit with code

**Test Entry:**
- Location: `app/src/test/java/com/anentropic/chuckd/ChuckDTestBase.java` (getReport method)
- Triggers: Unit test setup
- Responsibilities: Build arguments, parse into ChuckD instance, invoke getStructuredReport()

## Error Handling

**Strategy:** Exceptions bubble up with early termination

**Patterns:**
- IOException during schema loading: caught in `loadSchemas()`, propagated to caller with stack trace
- Provider instantiation failures: caught, printed to stderr, System.exit(1)
- File not found: IOException from Files.readString(), propagated
- Invalid schema content: ParsedSchema parsing fails, orElseThrow() propagates NoSuchElementException
- Incompatible schemas: Caught as list of SchemaIncompatibility, returned normally (not an error state)

## Cross-Cutting Concerns

**Logging:** Configured via Log4j with level set by `-l/--log-level` flag; defaults to OFF to minimize output

**Validation:**
- PicoCLI validates enum values for format, compatibility level, output format, log level
- File parameters must exist as readable files (PicoCLI path handling)
- Schema content validated by format-specific providers during parsing

**Authentication:** Not applicable; local file-based operation

**Output Formatting:**
- TEXT: Human-readable line format with path, type, direction, optional message
- JSON: Structured JSON array serialized via Jackson ObjectMapper

---

*Architecture analysis: 2026-03-07*
