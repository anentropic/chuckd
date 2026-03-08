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
            "person-narrowed.proto, person-base.proto",
    })
    public void testForwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/Pet", issue.path());
        assertEquals("forward", issue.direction());
        assertNull(issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-widened.proto, person-base.proto",
            "person-narrowed.proto, person-widened.proto, person-base.proto",
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
            "person-narrowed.proto, person-base.proto, person-base.proto",
    })
    public void testForwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/Pet", issue.path());
        assertEquals("forward", issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "person-widened.proto, person-base.proto",
    })
    public void testBackwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/Food", issue.path());
        assertEquals("backward", issue.direction());
        assertNull(issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-narrowed.proto, person-base.proto",
            "person-widened.proto, person-narrowed.proto, person-base.proto",
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
            "person-widened.proto, person-base.proto, person-base.proto",
    })
    public void testBackwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/Food", issue.path());
        assertEquals("backward", issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "Pet, forward, person-narrowed.proto, person-base.proto",
            "Food, backward, person-widened.proto, person-base.proto",
    })
    public void testFullIncompatible(
            String expectedMessage,
            String expectedDirection,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/" + expectedMessage, issue.path());
        assertEquals(expectedDirection, issue.direction());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.proto, person-base.proto",
            "person-narrowed.proto, person-base.proto, person-base.proto",
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
            "Pet, forward, person-narrowed.proto, person-base.proto, person-base.proto",
            "Food, backward, person-widened.proto, person-base.proto, person-base.proto",
    })
    public void testFullTransitiveIncompatible(
            String expectedMessage,
            String expectedDirection,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MESSAGE_REMOVED", issue.type());
        assertEquals("#/" + expectedMessage, issue.path());
        assertEquals(expectedDirection, issue.direction());
    }
}
