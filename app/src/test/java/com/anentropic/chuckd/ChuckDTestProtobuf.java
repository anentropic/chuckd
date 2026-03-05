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
public class ChuckDTestProtobuf extends ChuckDTestBase {
    static {
        resourcesSubDir = "protobuf";
        baseArgs = new String[] {"-f", "PROTOBUF"};
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.proto, person-narrowed.proto",
    })
    public void testForwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MESSAGE_REMOVED"), "Expected MESSAGE_REMOVED in: " + error);
        assertTrue(error.contains("#/Pet"), "Expected path #/Pet in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.proto, person-widened.proto",
            "person-base.proto, person-narrowed.proto, person-widened.proto",
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
            "person-base.proto, person-narrowed.proto, person-base.proto",
    })
    public void testForwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FORWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MESSAGE_REMOVED"), "Expected MESSAGE_REMOVED in: " + error);
        assertTrue(error.contains("#/Pet"), "Expected path #/Pet in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.proto, person-widened.proto",
    })
    public void testBackwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MESSAGE_REMOVED"), "Expected MESSAGE_REMOVED in: " + error);
        assertTrue(error.contains("#/Food"), "Expected path #/Food in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.proto, person-narrowed.proto",
            "person-base.proto, person-widened.proto, person-narrowed.proto",
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
            "person-base.proto, person-widened.proto, person-base.proto",
    })
    public void testBackwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "BACKWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MESSAGE_REMOVED"), "Expected MESSAGE_REMOVED in: " + error);
        assertTrue(error.contains("#/Food"), "Expected path #/Food in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "Pet, person-base.proto, person-narrowed.proto",  // incompatibility in -> direction
            "Food, person-base.proto, person-widened.proto",  // incompatibility in <- direction
    })
    public void testFullIncompatible(
            String expectedMessage,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MESSAGE_REMOVED"), "Expected MESSAGE_REMOVED in: " + error);
        assertTrue(error.contains("#/" + expectedMessage), "Expected path #/" + expectedMessage + " in: " + error);
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.proto, person-base.proto",
            "person-base.proto, person-narrowed.proto, person-base.proto",
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
            "Pet, person-base.proto, person-narrowed.proto, person-base.proto",  // incompatibility in -> direction
            "Food, person-base.proto, person-widened.proto, person-base.proto",  // incompatibility in <- direction
    })
    public void testFullTransitiveIncompatible(
            String expectedMessage,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        String error = report.get(0);
        assertTrue(error.contains("MESSAGE_REMOVED"), "Expected MESSAGE_REMOVED in: " + error);
        assertTrue(error.contains("#/" + expectedMessage), "Expected path #/" + expectedMessage + " in: " + error);
    }
}
