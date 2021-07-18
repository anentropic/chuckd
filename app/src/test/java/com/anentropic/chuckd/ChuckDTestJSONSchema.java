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

        assertEquals(1, report.size());
        assertEquals("Found incompatible change: Difference{jsonPath='#/properties/lastName/maxLength', type=MAX_LENGTH_ADDED}", report.get(0));
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

        assertEquals(1, report.size());
        assertEquals("Found incompatible change: Difference{jsonPath='#/properties/lastName/maxLength', type=MAX_LENGTH_ADDED}", report.get(0));
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

        assertEquals(1, report.size());
        assertEquals("Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}", report.get(0));
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

        assertEquals(1, report.size());
        assertEquals("Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}", report.get(0));
    }

    @ParameterizedTest
    @CsvSource({
            "lastName/maxLength, MAX_LENGTH_ADDED, person-base.json, person-narrowed.json",  // incompatibility in -> direction
            "age, TYPE_NARROWED, person-base.json, person-widened.json",  // incompatibility in <- direction
    })
    public void testFullIncompatible(
            String expectedPath,
            String expectedType,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
                String.format(
                        "Found incompatible change: Difference{jsonPath='#/properties/%s', type=%s}",
                        expectedPath,
                        expectedType
                ),
                report.get(0)
        );
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
            "lastName/maxLength, MAX_LENGTH_ADDED, person-base.json, person-narrowed.json, person-base.json",  // incompatibility in -> direction
            "age, TYPE_NARROWED, person-base.json, person-widened.json, person-base.json",  // incompatibility in <- direction
    })
    public void testFullTransitiveIncompatible(
            String expectedPath,
            String expectedType,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
                String.format(
                        "Found incompatible change: Difference{jsonPath='#/properties/%s', type=%s}",
                        expectedPath,
                        expectedType
                ),
                report.get(0)
        );
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

        assertEquals(1, report.size());
        assertEquals("Found incompatible change: Difference{jsonPath='#/items/properties/age', type=TYPE_NARROWED}", report.get(0));
    }
}
