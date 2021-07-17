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

    public List<String> getReport(String[] testcaseBaseArgs, String[] resources) throws IOException {
        List<String> args = new ArrayList<>();
        Collections.addAll(args, baseArgs);
        Collections.addAll(args, testcaseBaseArgs);
        for (String resPath : resources) {
            String arg = baseResourcesPath.resolve(resourcesSubDir).resolve(resPath).toFile().getAbsolutePath();
            args.add(arg);
        }
        cmd.parseArgs(args.toArray(new String[0]));

        return app.getReport();
    }
}
