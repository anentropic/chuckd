# Testing Patterns

**Analysis Date:** 2026-03-07

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) version 6.0.3
- Config: `app/build.gradle` lines 15-18, 33-38
- Platform launcher for IDE support: `org.junit.platform:junit-platform-launcher`

**Assertion Library:**
- Hamcrest 3.0 for matcher-based assertions
- JUnit Jupiter native assertions: `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`
- Imported via: `import static org.junit.jupiter.api.Assertions.*`

**Run Commands:**
```bash
./gradlew test                 # Run all tests
./gradlew test --watch        # Watch mode (if supported by Gradle)
./gradlew test --info         # Verbose output
```

**Gradle configuration (from `app/build.gradle`):**
```gradle
test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
    }
}
```

## Test File Organization

**Location:**
- Co-located structure: Tests mirror source package structure
- Main code: `app/src/main/java/com/anentropic/chuckd/*.java`
- Tests: `app/src/test/java/com/anentropic/chuckd/*.java`
- Test resources: `app/src/test/resources/` (schema files in subdirs: `jsonschema/`, `avro/`, `protobuf/`)

**Naming:**
- Test classes: `[ClassName]Test.java` - e.g., `CompatibilityReporterTest.java`
- Test base class: `ChuckDTestBase.java` for shared test setup
- Format-specific tests: `ChuckDTestAvro.java`, `ChuckDTestJSONSchema.java`, `ChuckDTestProtobuf.java`
- Test methods: `test[Scenario]()` in camelCase - e.g., `testForwardIncompatible()`, `testBackwardTransitiveIncompatible()`

**Files:**
```
app/src/test/java/com/anentropic/chuckd/
├── ChuckDTestBase.java                    # Base class with shared setup
├── ChuckDTestAvro.java                    # Avro format tests
├── ChuckDTestJSONSchema.java              # JSON Schema format tests
├── ChuckDTestProtobuf.java                # Protobuf format tests
├── CompatibilityReporterTest.java         # Unit tests for reporter
└── (future tests...)
```

## Test Structure

**Suite Organization:**
All tests are organized by format type, inheriting from base class. Each test class is a suite for a specific schema format.

**ChuckDTestBase organization:**
```java
public class ChuckDTestBase {
    Path baseResourcesPath = Paths.get("src", "test", "resources");

    ChuckD app;
    CommandLine cmd;

    protected static String resourcesSubDir;      // Format-specific subdir (set by subclass)
    protected static String[] baseArgs;           // Format-specific CLI args

    @BeforeEach
    public void setUp() {
        app = new ChuckD();
        cmd = new CommandLine(app);
    }

    public List<SchemaIncompatibility> getReport(...) throws IOException {
        // Helper to build args, parse, execute
    }
}
```

**Test method structure pattern:**
```java
@ParameterizedTest
@CsvSource({
    "schema1.avsc, schema2.avsc",
})
public void testScenarioName(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
    List<SchemaIncompatibility> report = getReport(
        new String[] {"-c", "BACKWARD"},
        resources
    );

    assertTrue(report.size() >= 1);
    SchemaIncompatibility issue = report.get(0);
    assertEquals("MISSING_UNION_BRANCH", issue.type());
    assertEquals("expected_path", issue.path());
    assertEquals("backward", issue.direction());
    assertTrue(issue.message().contains("LONG"));
}
```

**Test naming convention:**
- Compatible tests: `test[Feature]Compatible` - e.g., `testForwardCompatible`
- Incompatible tests: `test[Feature]Incompatible` - e.g., `testForwardTransitiveIncompatible`
- Feature variations: `testFull`, `testBackward`, `testForward`, `testBackwardTransitive`

## Test Structure

**Patterns:**

**Setup:**
- `@BeforeEach` annotation for per-test setup
- Creates new `ChuckD` instance and `CommandLine` wrapper
- Base class handles common initialization
- Static initializers in subclasses set `resourcesSubDir` and `baseArgs`

```java
@BeforeEach
public void setUp() {
    app = new ChuckD();
    cmd = new CommandLine(app);
}

static {
    resourcesSubDir = "avro";
    baseArgs = new String[] {"-f", "AVRO"};
}
```

**Teardown:**
- No explicit teardown required
- CLI command completes and exit code returned
- No state persisted between tests

**Assertion pattern:**
- Check result is non-empty: `assertTrue(report.size() >= 1)` or `assertFalse(issues.isEmpty())`
- Extract first issue: `SchemaIncompatibility issue = report.get(0)`
- Assert specific fields: type, path, direction, message
- Message assertions: `assertTrue(issue.message().contains(expectedType))`

## Mocking

**Framework:** No mocking framework used (no Mockito, EasyMock, etc. in dependencies)

**What IS tested:**
- Real schema files loaded from `app/src/test/resources/`
- Real schema provider implementations (Avro, JSON Schema, Protobuf)
- Real compatibility checking logic via Confluent library
- Real CLI argument parsing via PicoCLI

**Approach:**
- Integration-style testing: Full stack from CLI args to compatibility report
- Tests delegate to Confluent's underlying diff implementations
- "We assume that the underlying diff code from io.confluent works correctly" (from test comments)
- Focus: Verify correct integration with Confluent, not reimplementing their logic

**No explicit mocks/stubs:**
- Schema data: Provided via real files in resources
- Providers: Real Confluent provider classes instantiated
- File I/O: Real filesystem reads for test resources

## Fixtures and Factories

**Test Data:**
Test resources are actual schema files in various formats:

```
app/src/test/resources/
├── avro/
│   ├── person-base.avsc
│   ├── person-narrowed.avsc
│   └── person-widened.avsc
├── jsonschema/
│   ├── person-base.json
│   ├── person-narrowed.json
│   ├── person-widened.json
│   ├── people-1.0.0.json
│   └── people-1.1.0.json
└── protobuf/
    ├── person-base.proto
    ├── person-narrowed.proto
    └── person-widened.proto
```

**Loading pattern (from CompatibilityReporterTest):**
```java
static final Path JSON_RESOURCES = Paths.get("src", "test", "resources", "jsonschema");

@BeforeAll
static void loadSchemas() throws IOException {
    JsonSchemaProvider jsonProvider = new JsonSchemaProvider();
    jsonBase = loadSchema(jsonProvider, JSON_RESOURCES.resolve("person-base.json"));
    jsonNarrowed = loadSchema(jsonProvider, JSON_RESOURCES.resolve("person-narrowed.json"));
    jsonWidened = loadSchema(jsonProvider, JSON_RESOURCES.resolve("person-widened.json"));
}

private static ParsedSchema loadSchema(
        AbstractSchemaProvider provider, Path path
) throws IOException {
    String content = Files.readString(path);
    return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
}
```

**Fixture scope:**
- `@BeforeAll` static setup for shared schema instances across all tests in class
- Loaded once, reused across all test methods in that class
- Each test class loads its own format-specific schemas

**Custom aggregators:**
- `VarargsAggregator` in test base handles variable args from CSV sources
- Allows `@CsvSource` to pass variable number of schema file names

```java
static class VarargsAggregator implements ArgumentsAggregator {
    @Override
    public Object aggregateArguments(ArgumentsAccessor accessor, ParameterContext context) throws ArgumentsAggregationException {
        return accessor.toList().stream()
                .skip(context.getIndex())
                .map(String::valueOf)
                .toArray(String[]::new);
    }
}
```

## Coverage

**Requirements:** No coverage enforcement detected (no JaCoCo or similar in build.gradle)

**Strategy:**
- Multiple format-specific test classes ensure all schema formats tested
- Parameterized tests with multiple scenarios per test method
- Both positive (compatible) and negative (incompatible) test cases

**Test categories by CompatibilityReporterTest:**
1. Direction tests (backward, forward, full)
2. Transitivity tests (non-transitive vs transitive modes)
3. Parity tests (verify output matches Confluent's library behavior)
4. Format-specific field tests (JSON Schema, Avro, Protobuf)
5. Edge case tests (identical schemas = compatible)

**Example counts:**
- `CompatibilityReporterTest`: 15 test methods covering all directions, modes, formats
- `ChuckDTestAvro`: 8 parameterized test methods with multiple parameters each
- `ChuckDTestJSONSchema`: 8 parameterized test methods plus reference URI test
- `ChuckDTestProtobuf`: 8 parameterized test methods

## Test Types

**Unit Tests:**
- Scope: `CompatibilityReporterTest` tests the `CompatibilityReporter` class directly
- Approach: Instantiate reporter, call methods, assert results
- Does: Tests the core compatibility checking logic independent of CLI
- Focus: Verify direction/transitivity/parity behavior

**Integration Tests:**
- Scope: `ChuckDTest*` classes test full CLI argument parsing and execution
- Approach: Instantiate `ChuckD`, create `CommandLine`, parse args, execute
- Does: Tests that CLI correctly wires arguments to reporter
- Focus: Ensure `-f` format flag, `-c` compatibility level, resource resolution work correctly
- Covers: All three schema formats (Avro, JSON Schema, Protobuf)

**E2E Tests:**
- Not present: No black-box CLI execution tests
- Could be added: Running compiled `chuckd` binary with shell scripts
- Current approach: Integration tests via Java CLI framework sufficient

## Common Patterns

**Parameterized Tests:**
Tests use `@ParameterizedTest` with `@CsvSource` to run same test logic with different inputs:

```java
@ParameterizedTest
@CsvSource({
    "person-base.avsc, person-narrowed.avsc",
})
public void testForwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
    List<SchemaIncompatibility> report = getReport(
        new String[] {"-c", "FORWARD"},
        resources
    );
    // assertions...
}
```

**Multiple parameters with aggregator:**
```java
@ParameterizedTest
@CsvSource({
    "LONG, forward, person-base.avsc, person-narrowed.avsc",
    "DOUBLE, backward, person-base.avsc, person-widened.avsc",
})
public void testFullIncompatible(
        String expectedWriterType,
        String expectedDirection,
        @AggregateWith(VarargsAggregator.class) String... resources
) throws IOException {
    // First two params from CSV, rest aggregated as String[]
}
```

**Async Testing:**
Not applicable: Methods are synchronous. IOException propagated to test framework.

```java
public void testMethod() throws IOException {
    // IOException declared, not caught
}
```

**Error Testing:**
Tests verify specific error conditions by checking report contents:

```java
@Test
void testParityBackward() {
    assertParity(CompatibilityLevel.BACKWARD, CompatibilityChecker.BACKWARD_CHECKER,
            jsonBase, List.of(jsonWidened), SchemaFormat.JSONSCHEMA);
}
```

Error details are embedded in `SchemaIncompatibility` objects and validated:
- `issue.type()` - type of incompatibility
- `issue.message()` - human-readable message (Avro only)
- `issue.direction()` - backward/forward direction

**Assertions for compatibility:**
- Empty list = compatible: `assertTrue(issues.isEmpty())`
- Non-empty list = incompatible: `assertFalse(issues.isEmpty())`
- Size checks: `assertEquals(0, report.size())` for compatible, `assertTrue(report.size() >= 1)` for incompatible

## Test Execution

**Run all tests:**
```bash
./gradlew test
```

**Run specific test class:**
```bash
./gradlew test --tests CompatibilityReporterTest
./gradlew test --tests ChuckDTestAvro
```

**Run with output:**
```bash
./gradlew test --info
```

**Test logging (from build.gradle):**
```gradle
testLogging {
    events "passed", "skipped", "failed"
}
```

This outputs: Pass/fail/skip status, test names, failure messages.

---

*Testing analysis: 2026-03-07*
