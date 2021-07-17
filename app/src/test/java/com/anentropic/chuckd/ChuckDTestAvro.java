package com.anentropic.chuckd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        List<String> report = getReport(
                new String[] {"-c", "FORWARD"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
            "Incompatibility{type:MISSING_UNION_BRANCH, location:/fields/0/type/1, " +
            "message:reader union lacking writer type: LONG, " +
            "reader:[\"null\",\"int\"], writer:[\"null\",\"long\"]}",
            report.get(0)
        );
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
        List<String> report = getReport(
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
        List<String> report = getReport(
                new String[] {"-c", "FORWARD_TRANSITIVE"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
            "Incompatibility{type:MISSING_UNION_BRANCH, location:/fields/0/type/1, " +
            "message:reader union lacking writer type: LONG, " +
            "reader:[\"null\",\"int\"], writer:[\"null\",\"long\"]}",
            report.get(0)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "person-base.avsc, person-widened.avsc",
    })
    public void testBackwardIncompatible(@AggregateWith(VarargsAggregator.class) String... resources) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "BACKWARD"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
            "Incompatibility{type:MISSING_UNION_BRANCH, location:/fields/0/type/1, " +
            "message:reader union lacking writer type: DOUBLE, " +
            "reader:[\"null\",\"long\"], writer:[\"null\",\"double\"]}",
            report.get(0)
        );
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
        List<String> report = getReport(
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
        List<String> report = getReport(
                new String[] {"-c", "BACKWARD_TRANSITIVE"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
            "Incompatibility{type:MISSING_UNION_BRANCH, location:/fields/0/type/1, " +
            "message:reader union lacking writer type: DOUBLE, " +
            "reader:[\"null\",\"long\"], writer:[\"null\",\"double\"]}",
            report.get(0)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "LONG, int, long, person-base.avsc, person-narrowed.avsc",  // incompatibility in -> direction
            "DOUBLE, long, double, person-base.avsc, person-widened.avsc",  // incompatibility in <- direction
    })
    public void testFullIncompatible(
            String expectedType,
            String readerType,
            String writerType,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
                String.format(
                        "Incompatibility{type:MISSING_UNION_BRANCH, location:/fields/0/type/1, " +
                        "message:reader union lacking writer type: %s, " +
                        "reader:[\"null\",\"%s\"], writer:[\"null\",\"%s\"]}",
                        expectedType,
                        readerType,
                        writerType
                ),
                report.get(0)
        );
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
         List<String> report = getReport(
                new String[] {"-c", "FULL"},
                resources
        );
        assertEquals(0, report.size());
    }

    @ParameterizedTest
    @CsvSource({
            "LONG, int, long, person-base.avsc, person-narrowed.avsc, person-base.avsc",  // incompatibility in -> direction
            "DOUBLE, long, double, person-base.avsc, person-widened.avsc, person-base.avsc",  // incompatibility in <- direction
    })
    public void testFullTransitiveIncompatible(
            String expectedType,
            String readerType,
            String writerType,
            @AggregateWith(VarargsAggregator.class) String... resources
    ) throws IOException {
        List<String> report = getReport(
                new String[] {"-c", "FULL_TRANSITIVE"},
                resources
        );

        assertEquals(1, report.size());
        assertEquals(
                String.format(
                    "Incompatibility{type:MISSING_UNION_BRANCH, location:/fields/0/type/1, " +
                    "message:reader union lacking writer type: %s, " +
                    "reader:[\"null\",\"%s\"], writer:[\"null\",\"%s\"]}",
                    expectedType,
                    readerType,
                    writerType
            ),
                report.get(0)
        );
    }
}
