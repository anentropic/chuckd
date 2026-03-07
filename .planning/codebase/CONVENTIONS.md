# Coding Conventions

**Analysis Date:** 2026-03-07

## Naming Patterns

**Files:**
- Java files use PascalCase: `ChuckD.java`, `CompatibilityReporter.java`, `SchemaIncompatibility.java`
- Test files follow pattern: `[ClassName]Test.java` or `[ClassName]TestBase.java` for test classes
- Files match the public class name within them

**Functions/Methods:**
- Use camelCase: `loadSchemas()`, `comparePair()`, `getStructuredReport()`, `formatText()`
- Getter methods use `get` prefix: `getStructuredReport()`, `getVersion()`
- Methods are typically public or private, package-private when needed

**Variables:**
- Local variables: camelCase: `newSchema`, `previousSchemas`, `schemaFormat`, `toCheck`
- Class fields: camelCase with explicit visibility modifiers
- Static constants: UPPER_SNAKE_CASE: `COMPATIBLE_CHANGES`
- Path variables: camelCase with `Path` suffix: `baseResourcesPath`

**Types/Classes:**
- Enums: PascalCase: `SchemaFormat`, `CompatibilityLevel`, `LogLevel`, `OutputFormat`
- Enum values: UPPER_SNAKE_CASE: `JSONSCHEMA`, `BACKWARD`, `FORWARD_TRANSITIVE`
- Records: PascalCase: `SchemaIncompatibility`
- Interfaces/Abstract classes: PascalCase with suffix: `ArgumentsAggregator` (from JUnit)

**Parameters:**
- Method parameters: camelCase
- Annotated parameters use annotations above: `@Parameters`, `@Option`, `@AggregateWith`
- Varargs: `String... args`, `String... resources`

## Code Style

**Formatting:**
- 4-space indentation (standard Java)
- Opening braces on same line: `public void method() {`
- Method and class declarations use space before opening brace
- Statements: one statement per line

**Linting:**
- No linting config files detected in the project root
- Code follows standard Java conventions implicitly
- Imports are organized: standard library first, then third-party, then project classes

**Organization:**
- Classes are organized with static initializers at top, fields, then methods
- Public methods before private helper methods
- Constructor call: `@BeforeEach` setup methods in test base class

## Import Organization

**Order (observed in code):**
1. Standard Java imports: `java.io.*`, `java.lang.*`, `java.nio.*`, `java.util.*`, `java.util.concurrent.*`
2. Third-party framework imports: `com.fasterxml.jackson.*`, `io.confluent.*`, `org.apache.*`, `org.joda.*`
3. JUnit imports: `org.junit.jupiter.*`
4. PicoCLI imports: `picocli.*`

**Examples from codebase:**
- `ChuckD.java`: Imports organized by stdlib, then Confluent, then Jackson, then PicoCLI
- Test files: Java imports first, then JUnit, then PicoCLI, then static imports (`import static org.junit.jupiter.api.Assertions.*`)
- Static imports used for assertion methods: `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`

## Error Handling

**Patterns:**
- Direct throws in method signatures: `throws IOException` in main methods
- Try-catch with `printStackTrace()` for initialization errors: `e.printStackTrace(); System.exit(1);`
- Used in `loadSchemas()` method when reflecting provider instances
- `orElseThrow()` for Optional handling: `provider.parseSchema(...).orElseThrow()`

**Exception propagation:**
- Test methods declare throws: `public void testMethod() throws IOException`
- Factory methods can throw checked exceptions
- No custom exception types defined in codebase; uses standard library exceptions

**Exit codes:**
- Return exit code as Integer from `Callable<Integer>` implementation
- Exit code = number of incompatibilities found (non-zero = failure)
- `System.exit(exitCode)` used in main method

## Logging

**Framework:** No explicit logging framework. Uses `org.apache.log4j` for schema provider configuration only.

**Patterns:**
- `BasicConfigurator.configure()` in `configureRootLogger()` to set up logging
- Log level set dynamically from CLI option: `LogManager.getRootLogger().setLevel(Level.toLevel(logLevel.name()))`
- No application-level logging in business logic
- System output via `System.out.print()` for formatted results
- `e.printStackTrace()` used for initialization error reporting

**Approach:**
- Console-based output only
- Structured output formats (TEXT or JSON) controlled by `--output` flag

## Comments

**When to Comment:**
- Block comments at method level explaining non-obvious logic
- Example: Comments explaining argument order for JSON Schema and Protobuf diff comparisons
- Comments in test classes explaining test assumptions

**JSDoc/Documentation:**
- No JSDoc comments observed in main code
- Inline comments used for complex logic: See `CompatibilityReporter.java` lines 75-81 explaining backward/forward compatibility logic
- Static initializer blocks in test classes have comments: `// Protobuf diff: compare(original, update)`

**Example comment style (from code):**
```java
// JSON Schema diff: compare(original, update) finds changes from original -> update
// For backward: reader=new, writer=old -- new can read old
```

## Function Design

**Size:** Methods are generally small and focused:
- Largest method: `CompatibilityReporter.comparePair()` at ~70 lines (but mostly format-specific delegated logic)
- Most helper methods: 5-20 lines
- Pattern: delegate to format-specific implementations

**Parameters:**
- Methods typically 2-5 parameters
- Use records for complex return types: `SchemaIncompatibility` record
- Varargs used for CLI argument handling: `String... args`
- List parameters for schema collections

**Return Values:**
- `List<SchemaIncompatibility>` for compatibility check results (empty list = compatible)
- `String` for formatted output
- `Integer` for exit codes
- `Optional<ParsedSchema>` from third-party; extracted via `orElseThrow()`

**Method visibility:**
- Public: CLI command execution, test helper methods
- Private: Implementation details, format-specific logic
- Package-private: Schema providers, internal classes (via no modifier)

## Module Design

**Exports:**
- Single main class: `ChuckD` implements `Callable<Integer>` for PicoCLI
- Helper classes: `CompatibilityReporter`, `VersionProvider`, `SchemaIncompatibility` (record)
- All classes in same package: `com.anentropic.chuckd`

**Barrel Files:**
- No barrel/index files pattern used
- All classes directly importable from package

**Package organization:**
- All main code in: `app/src/main/java/com/anentropic/chuckd/`
- All test code in: `app/src/test/java/com/anentropic/chuckd/`
- Follows Maven/Gradle standard structure

## Type System

**Generics:**
- Used with collections: `List<ParsedSchema>`, `List<SchemaIncompatibility>`
- Used with Maps: `Map<SchemaFormat, Class<? extends AbstractSchemaProvider>>`
- Bounded type parameters: `Class<? extends AbstractSchemaProvider>` for schema provider reflection

**Null handling:**
- Records include nullable fields: `String message` in `SchemaIncompatibility` (null for JSON Schema/Protobuf)
- `null` assertions in tests: `assertNull(issue.message())`, `assertNotNull(issue.message())`
- No Optional wrapper for optional fields

## Records and Data Classes

**Pattern:** Uses Java Records for immutable data transfer:
```java
public record SchemaIncompatibility(
    String type,       // enum name
    String path,       // JSON pointer/path
    String direction,  // "backward" or "forward"
    String message     // human-readable detail, nullable
) {}
```

- Record eliminates boilerplate: auto-generates constructor, getters, equals, hashCode, toString
- No mutable properties
- Direct field access via accessor methods: `issue.type()`, `issue.path()`

## Testing Conventions

Covered in TESTING.md, but note:
- Test class naming: `[Subject]Test` or `[Subject]TestBase`
- Test method naming: `test[Scenario]()` in camelCase
- Parameterized tests use `@ParameterizedTest` and `@CsvSource`

---

*Convention analysis: 2026-03-07*
