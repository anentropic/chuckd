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
public class ChuckDTestAvro extends ChuckDTestBase {
    static {
        resourcesSubDir = "avro";
        baseArgs = new String[] {"-f", "AVRO"};
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.avsc, person-narrowed.avsc",
    })
    public void testForwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals("forward", issue.direction());
        assertTrue(issue.message().contains("reader union lacking writer type: LONG"),
                "Expected writer type LONG in: " + issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.avsc, person-widened.avsc",
            "person-base.avsc, person-narrowed.avsc, person-widened.avsc",
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
            "person-base.avsc, person-narrowed.avsc, person-base.avsc",
    })
    public void testForwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FORWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals("forward", issue.direction());
        assertTrue(issue.message().contains("reader union lacking writer type: LONG"),
                "Expected writer type LONG in: " + issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.avsc, person-widened.avsc",
    })
    public void testBackwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals("backward", issue.direction());
        assertTrue(issue.message().contains("reader union lacking writer type: DOUBLE"),
                "Expected writer type DOUBLE in: " + issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.avsc, person-narrowed.avsc",
            "person-base.avsc, person-widened.avsc, person-narrowed.avsc",
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
            "person-base.avsc, person-widened.avsc, person-base.avsc",
    })
    public void testBackwardTransitiveIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "BACKWARD_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals("backward", issue.direction());
        assertTrue(issue.message().contains("reader union lacking writer type: DOUBLE"),
                "Expected writer type DOUBLE in: " + issue.message());
    }

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
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals(expectedDirection, issue.direction());
        assertTrue(issue.message().contains(expectedWriterType),
                "Expected " + expectedWriterType + " in: " + issue.message());
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.avsc, person-base.avsc",
            "person-base.avsc, person-narrowed.avsc, person-base.avsc",
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
            "LONG, forward, person-base.avsc, person-narrowed.avsc, person-base.avsc",
            "DOUBLE, backward, person-base.avsc, person-widened.avsc, person-base.avsc",
    })
    public void testFullTransitiveIncompatible(
            String expectedWriterType,
            String expectedDirection,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<SchemaIncompatibility> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertTrue(report.size() >= 1);
        SchemaIncompatibility issue = report.get(0);
        assertEquals("MISSING_UNION_BRANCH", issue.type());
        assertEquals("/fields/0/type/1", issue.path());
        assertEquals(expectedDirection, issue.direction());
        assertTrue(issue.message().contains(expectedWriterType),
                "Expected " + expectedWriterType + " in: " + issue.message());
    }
}
