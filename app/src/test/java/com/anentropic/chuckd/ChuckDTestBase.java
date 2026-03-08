package com.anentropic.chuckd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;

import picocli.CommandLine;


public class ChuckDTestBase {
    Path baseResourcesPath = Paths.get("src","test", "resources");

    ChuckD app;
    CommandLine cmd;

    protected static String resourcesSubDir;
    protected static String[] baseArgs;

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

    /**
     * Build the argument list and call getStructuredReport() directly.
     *
     * Resources are appended as positional args in CsvSource order.
     * After arg order reversal: the LAST resource is the "new" schema,
     * all prior resources are "previous" schemas (oldest to newest).
     *
     * CsvSource patterns in subclasses must list previous schemas first
     * and the new schema last.
     */
    public List<SchemaIncompatibility> getReport(String[] testcaseBaseArgs, String[] resources) throws IOException {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, baseArgs);
        Collections.addAll(args, testcaseBaseArgs);

        List<Path> resolvedPaths = new ArrayList<>();
        for (String resPath : resources) {
            Path resolved = baseResourcesPath.resolve(resourcesSubDir).resolve(resPath).toAbsolutePath();
            resolvedPaths.add(resolved);
            args.add(resolved.toString());
        }

        // Parse options (but not positional args) so that flags like -c, -f, -o are applied
        cmd.parseArgs(args.toArray(new String[0]));

        // Use the new path-based API: last path = new schema, rest = previous
        Path newSchemaPath = resolvedPaths.get(resolvedPaths.size() - 1);
        List<Path> previousPaths = resolvedPaths.subList(0, resolvedPaths.size() - 1);

        return app.getStructuredReport(newSchemaPath, previousPaths);
    }
}
