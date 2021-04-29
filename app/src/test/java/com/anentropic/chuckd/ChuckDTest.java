package com.anentropic.chuckd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;
import org.junit.jupiter.params.provider.CsvSource;

import picocli.CommandLine;

/*
    We assume that the underlying diff code from io.confluent works correctly
    but we want to test that we have integrated correctly
 */
public class ChuckDTest {
    Path resourcesPath = Paths.get("src","test", "resources");

    ChuckD app;
    CommandLine cmd;

    @BeforeEach
    public void setUp() {
        app = new ChuckD();
        cmd = new CommandLine(app);
    }

    static class VarargsAggregator implements ArgumentsAggregator {
        /*
            Java sucks...
            why is this not part of JUnit?
            why is CsvSource the easiest way to pass multiple args?
            https://github.com/junit-team/junit5/issues/2256
         */
        @Override
        public Object aggregateArguments(ArgumentsAccessor accessor, ParameterContext context) throws ArgumentsAggregationException {
            return accessor.toList().stream()
                    .skip(context.getIndex())
                    .map(String::valueOf)
                    .toArray(String[]::new);
        }
    }

    public List<String> getReport(String[] base_args, String[] resources) throws IOException {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, base_args);
        for (String resPath : resources) {
            String arg = resourcesPath.resolve(resPath).toFile().getAbsolutePath();
            args.add(arg);
        }
        cmd.parseArgs(args.toArray(new String[0]));

        return app.getReport();
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
