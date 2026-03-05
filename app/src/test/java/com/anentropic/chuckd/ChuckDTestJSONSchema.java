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
        List<String> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MAX_LENGTH_ADDED"), "Expected MAX_LENGTH_ADDED in: " + error);
        assertTrue(error.contains("#/properties/lastName/maxLength"), "Expected path #/properties/lastName/maxLength in: " + error);
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
        List<String> report = getReport(
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
        List<String> report = getReport(
                new String[] {"-c", "FORWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MAX_LENGTH_ADDED"), "Expected MAX_LENGTH_ADDED in: " + error);
        assertTrue(error.contains("#/properties/lastName/maxLength"), "Expected path #/properties/lastName/maxLength in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.json, person-widened.json",
    })
    public void testBackwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("TYPE_NARROWED"), "Expected TYPE_NARROWED in: " + error);
        assertTrue(error.contains("#/properties/age"), "Expected path #/properties/age in: " + error);
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
        List<String> report = getReport(
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
        List<String> report = getReport(
                new String[] {"-c", "BACKWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("TYPE_NARROWED"), "Expected TYPE_NARROWED in: " + error);
        assertTrue(error.contains("#/properties/age"), "Expected path #/properties/age in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "MAX_LENGTH_ADDED, #/properties/lastName/maxLength, person-base.json, person-narrowed.json",  // incompatibility in -> direction
            "TYPE_NARROWED, #/properties/age, person-base.json, person-widened.json",  // incompatibility in <- direction
    })
    public void testFullIncompatible(
            String expectedType,
            String expectedPath,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains(expectedType), "Expected " + expectedType + " in: " + error);
        assertTrue(error.contains(expectedPath), "Expected path '" + expectedPath + "' in: " + error);
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
         List<String> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );
        assertEquals(0, report.size());
    }

    @ParameterizedTest
    @CsvSource({
            "MAX_LENGTH_ADDED, #/properties/lastName/maxLength, person-base.json, person-narrowed.json, person-base.json",  // incompatibility in -> direction
            "TYPE_NARROWED, #/properties/age, person-base.json, person-widened.json, person-base.json",  // incompatibility in <- direction
    })
    public void testFullTransitiveIncompatible(
            String expectedType,
            String expectedPath,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains(expectedType), "Expected " + expectedType + " in: " + error);
        assertTrue(error.contains(expectedPath), "Expected path '" + expectedPath + "' in: " + error);
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
        List<String> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("TYPE_NARROWED"), "Expected TYPE_NARROWED in: " + error);
        assertTrue(error.contains("#/items/properties/age"), "Expected path #/items/properties/age in: " + error);
    }
}
