package com.anentropic.chuckd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class CompatibilityReporterTest {

    static final Path JSON_RESOURCES = Paths.get("src", "test", "resources", "jsonschema");
    static final Path AVRO_RESOURCES = Paths.get("src", "test", "resources", "avro");
    static final Path PROTOBUF_RESOURCES = Paths.get("src", "test", "resources", "protobuf");

    static ParsedSchema jsonBase, jsonNarrowed, jsonWidened;
    static ParsedSchema avroBase, avroNarrowed, avroWidened;
    static ParsedSchema protoBase, protoNarrowed, protoWidened;

    static CompatibilityReporter reporter = new CompatibilityReporter();

    @BeforeAll
    static void loadSchemas() throws IOException {
        JsonSchemaProvider jsonProvider = new JsonSchemaProvider();
        jsonBase = loadSchema(jsonProvider, JSON_RESOURCES.resolve("person-base.json"));
        jsonNarrowed = loadSchema(jsonProvider, JSON_RESOURCES.resolve("person-narrowed.json"));
        jsonWidened = loadSchema(jsonProvider, JSON_RESOURCES.resolve("person-widened.json"));

        AvroSchemaProvider avroProvider = new AvroSchemaProvider();
        avroBase = loadSchema(avroProvider, AVRO_RESOURCES.resolve("person-base.avsc"));
        avroNarrowed = loadSchema(avroProvider, AVRO_RESOURCES.resolve("person-narrowed.avsc"));
        avroWidened = loadSchema(avroProvider, AVRO_RESOURCES.resolve("person-widened.avsc"));

        ProtobufSchemaProvider protoProvider = new ProtobufSchemaProvider();
        protoBase = loadSchema(protoProvider, PROTOBUF_RESOURCES.resolve("person-base.proto"));
        protoNarrowed = loadSchema(protoProvider, PROTOBUF_RESOURCES.resolve("person-narrowed.proto"));
        protoWidened = loadSchema(protoProvider, PROTOBUF_RESOURCES.resolve("person-widened.proto"));
    }

    private static ParsedSchema loadSchema(
            io.confluent.kafka.schemaregistry.AbstractSchemaProvider provider, Path path
    ) throws IOException {
        String content = Files.readString(path);
        return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
    }

    // --- Direction tests ---

    @Test
    void testBackwardReturnsCorrectDirection() {
        // new=base, previous=[widened] -> backward incompatible (TYPE_NARROWED)
        // because base (reader) can't read widened (writer) data: number->integer is narrowing
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.BACKWARD, jsonBase, List.of(jsonWidened), SchemaFormat.JSONSCHEMA);
        assertFalse(issues.isEmpty());
        for (SchemaIncompatibility issue : issues) {
            assertEquals("backward", issue.direction());
        }
    }

    @Test
    void testForwardReturnsCorrectDirection() {
        // new=base, previous=[narrowed] -> forward incompatible (MAX_LENGTH_ADDED)
        // because narrowed (old reader) can't read base (new writer) data: maxLength constraint missing
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.FORWARD, jsonBase, List.of(jsonNarrowed), SchemaFormat.JSONSCHEMA);
        assertFalse(issues.isEmpty());
        for (SchemaIncompatibility issue : issues) {
            assertEquals("forward", issue.direction());
        }
    }

    @Test
    void testFullReturnsBothDirections() {
        // FULL with new=base, previous=[narrowed] -> forward incompatible only
        List<SchemaIncompatibility> forwardIssues = reporter.check(
                CompatibilityLevel.FULL, jsonBase, List.of(jsonNarrowed), SchemaFormat.JSONSCHEMA);
        assertFalse(forwardIssues.isEmpty());
        assertTrue(forwardIssues.stream().allMatch(i -> "forward".equals(i.direction())));

        // FULL with new=base, previous=[widened] -> backward incompatible only
        List<SchemaIncompatibility> backwardIssues = reporter.check(
                CompatibilityLevel.FULL, jsonBase, List.of(jsonWidened), SchemaFormat.JSONSCHEMA);
        assertFalse(backwardIssues.isEmpty());
        assertTrue(backwardIssues.stream().allMatch(i -> "backward".equals(i.direction())));
    }

    // --- Transitivity tests ---

    @Test
    void testNonTransitiveChecksOnlyLatest() {
        // Forward non-transitive with new=base:
        //   previous = [narrowed (incompat), widened (compat)]
        //   Should only check against widened (last) -> compatible -> empty
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.FORWARD,
                jsonBase,
                List.of(jsonNarrowed, jsonWidened),
                SchemaFormat.JSONSCHEMA
        );
        assertTrue(issues.isEmpty());
    }

    @Test
    void testTransitiveChecksAll() {
        // Same setup but transitive -- should find incompatibility from narrowed
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.FORWARD_TRANSITIVE,
                jsonBase,
                List.of(jsonNarrowed, jsonWidened),
                SchemaFormat.JSONSCHEMA
        );
        assertFalse(issues.isEmpty());
    }

    @Test
    void testTransitiveFailsFast() {
        // Transitive checks most recent first, fails fast on first incompatible
        // Forward with new=base, previous=[narrowed, narrowed]
        // Most recent (last in list, first after reverse) = narrowed -> incompatible -> fail fast
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.FORWARD_TRANSITIVE,
                jsonBase,
                List.of(jsonNarrowed, jsonNarrowed),
                SchemaFormat.JSONSCHEMA
        );
        assertFalse(issues.isEmpty());
        // Verify count matches a single comparison (fail-fast, not accumulated)
        List<SchemaIncompatibility> singleCheck = reporter.check(
                CompatibilityLevel.FORWARD,
                jsonBase,
                List.of(jsonNarrowed),
                SchemaFormat.JSONSCHEMA
        );
        assertEquals(singleCheck.size(), issues.size());
    }

    // --- Parity tests with Confluent's CompatibilityChecker ---

    @Test
    void testParityBackward() {
        assertParity(CompatibilityLevel.BACKWARD, CompatibilityChecker.BACKWARD_CHECKER,
                jsonBase, List.of(jsonWidened), SchemaFormat.JSONSCHEMA);
    }

    @Test
    void testParityForward() {
        assertParity(CompatibilityLevel.FORWARD, CompatibilityChecker.FORWARD_CHECKER,
                jsonBase, List.of(jsonNarrowed), SchemaFormat.JSONSCHEMA);
    }

    @Test
    void testParityFull() {
        assertParity(CompatibilityLevel.FULL, CompatibilityChecker.FULL_CHECKER,
                jsonBase, List.of(jsonNarrowed), SchemaFormat.JSONSCHEMA);
    }

    @Test
    void testParityBackwardTransitive() {
        assertParity(CompatibilityLevel.BACKWARD_TRANSITIVE, CompatibilityChecker.BACKWARD_TRANSITIVE_CHECKER,
                jsonBase, List.of(jsonWidened, jsonWidened), SchemaFormat.JSONSCHEMA);
    }

    @Test
    void testParityForwardTransitive() {
        assertParity(CompatibilityLevel.FORWARD_TRANSITIVE, CompatibilityChecker.FORWARD_TRANSITIVE_CHECKER,
                jsonBase, List.of(jsonNarrowed, jsonNarrowed), SchemaFormat.JSONSCHEMA);
    }

    @Test
    void testParityFullTransitive() {
        assertParity(CompatibilityLevel.FULL_TRANSITIVE, CompatibilityChecker.FULL_TRANSITIVE_CHECKER,
                jsonBase, List.of(jsonNarrowed, jsonNarrowed), SchemaFormat.JSONSCHEMA);
    }

    private void assertParity(
            CompatibilityLevel level,
            CompatibilityChecker checker,
            ParsedSchema newSchema,
            List<ParsedSchema> previousSchemas,
            SchemaFormat format
    ) {
        List<SchemaIncompatibility> ourResult = reporter.check(level, newSchema, previousSchemas, format);
        List<String> confluentResult = checker.isCompatible(newSchema, previousSchemas);
        // Filter out metadata entries from Confluent result
        long confluentCount = confluentResult.stream()
                .filter(s -> !s.startsWith("{oldSchema:"))
                .count();
        assertEquals(confluentCount, ourResult.size(),
                "Parity mismatch for " + level + ": confluent=" + confluentCount + " ours=" + ourResult.size());
    }

    // --- Format-specific field tests ---

    @Test
    void testJsonSchemaFields() {
        // new=base, previous=[widened] -> backward incompatible: TYPE_NARROWED at #/properties/age
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.BACKWARD, jsonBase, List.of(jsonWidened), SchemaFormat.JSONSCHEMA);
        assertFalse(issues.isEmpty());
        SchemaIncompatibility issue = issues.get(0);
        assertEquals("TYPE_NARROWED", issue.type());
        assertEquals("#/properties/age", issue.path());
        assertEquals("backward", issue.direction());
        assertNull(issue.message());
    }

    @Test
    void testAvroFields() {
        // new=base, previous=[widened] -> backward incompatible: MISSING_UNION_BRANCH
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.BACKWARD, avroBase, List.of(avroWidened), SchemaFormat.AVRO);
        assertFalse(issues.isEmpty());
        SchemaIncompatibility issue = issues.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals("backward", issue.direction());
        assertNotNull(issue.message());
        assertTrue(issue.message().contains("DOUBLE"));
    }

    @Test
    void testProtobufFields() {
        // new=base, previous=[narrowed] -> forward incompatible: MESSAGE_REMOVED at #/Pet
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.FORWARD, protoBase, List.of(protoNarrowed), SchemaFormat.PROTOBUF);
        assertFalse(issues.isEmpty());
        SchemaIncompatibility issue = issues.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/Pet", issue.path());
        assertEquals("forward", issue.direction());
        assertNull(issue.message());
    }

    // --- Edge cases ---

    @Test
    void testCompatibleSchemasReturnEmpty() {
        List<SchemaIncompatibility> issues = reporter.check(
                CompatibilityLevel.BACKWARD, jsonBase, List.of(jsonBase), SchemaFormat.JSONSCHEMA);
        assertTrue(issues.isEmpty());
    }
}
