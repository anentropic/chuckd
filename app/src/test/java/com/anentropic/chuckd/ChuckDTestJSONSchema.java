package com.anentropic.chuckd;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.provider.CsvSource;

/*
    We assume that the underlying diff code from io.confluent works correctly
    but we want to test that we have integrated correctly
 */
public class ChuckDTestJSONSchema extends ChuckDTestBase {
    static {
        resourcesSubDir = "jsonschema";
        baseArgs = new String[] {};
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-narrowed.json",
    })
    public void testForwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MAX_LENGTH_ADDED", issue.type());
        assertEquals("#/properties/lastName/maxLength", issue.path());
        assertEquals("forward", issue.direction());
        assertNull(issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-widened.json",
            "person-base.json, person-narrowed.json, person-widened.json",
    })
    public void testForwardCompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        /*
            non-transitive compatibility, should ignore incompatible "older" schema
         */
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertEquals(0, report.size());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-narrowed.json, person-base.json",
    })
    public void testForwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MAX_LENGTH_ADDED", issue.type());
        assertEquals("#/properties/lastName/maxLength", issue.path());
        assertEquals("forward", issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-widened.json",
    })
    public void testBackwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("TYPE_NARROWED", issue.type());
        assertEquals("#/properties/age", issue.path());
        assertEquals("backward", issue.direction());
        assertNull(issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-narrowed.json",
            "person-base.json, person-widened.json, person-narrowed.json",
    })
    public void testBackwardCompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        /*
            non-transitive compatibility, should ignore incompatible "older" schema
         */
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertEquals(0, report.size());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-widened.json, person-base.json",
    })
    public void testBackwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("TYPE_NARROWED", issue.type());
        assertEquals("#/properties/age", issue.path());
        assertEquals("backward", issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "MAX_LENGTH_ADDED, #/properties/lastName/maxLength, forward, person-base.json, person-narrowed.json",
            "TYPE_NARROWED, #/properties/age, backward, person-base.json, person-widened.json",
    })
    public void testFullIncompatible(
            String expectedType,
            String expectedPath,
            String expectedDirection,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals(expectedType, issue.type());
        assertEquals(expectedPath, issue.path());
        assertEquals(expectedDirection, issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-base.json",
            "person-base.json, person-narrowed.json, person-base.json",
    })
    public void testFullCompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        /*
            non-transitive compatibility, should ignore incompatible "older" schema
         */
         List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );
        assertEquals(0, report.size());
    }

    @ParameterizedTest
    @CsvSource({
            "MAX_LENGTH_ADDED, #/properties/lastName/maxLength, forward, person-base.json, person-narrowed.json, person-base.json",
            "TYPE_NARROWED, #/properties/age, backward, person-base.json, person-widened.json, person-base.json",
    })
    public void testFullTransitiveIncompatible(
            String expectedType,
            String expectedPath,
            String expectedDirection,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals(expectedType, issue.type());
        assertEquals(expectedPath, issue.path());
        assertEquals(expectedDirection, issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "people-1.1.0.json, people-1.0.0.json",
    })
    public void testRefURIsResolved(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        /*
            the "people" schema includes "person" schema via an (https) $ref
            we assume that io.confluent handles this correctly, this is a basic smoke test
            to check we have integrated correctly and it still works here
         */
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("TYPE_NARROWED", issue.type());
        assertEquals("#/items/properties/age", issue.path());
        assertEquals("forward", issue.direction());
    }
}
