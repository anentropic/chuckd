package com.anentropic.chuckd;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.joda.time.DateTimeZone;
import org.joda.time.tz.UTCProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

enum CompatibilityLevel {
    BACKWARD,
    FORWARD,
    FULL,
    BACKWARD_TRANSITIVE,
    FORWARD_TRANSITIVE,
    FULL_TRANSITIVE,
};

enum LogLevel {
    OFF,
    ALL,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
};

@Command(name = "chuckd",
        mixinStandardHelpOptions = true,
        description = "Report evolution compatibility of latest vs existing schema versions.",
        versionProvider = VersionProvider.class)
class ChuckD implements Callable<Integer> {

    @Option(names = {"-c", "--compatibility"},
            defaultValue = "FORWARD_TRANSITIVE",
            description = "Valid values: ${COMPLETION-CANDIDATES}\n" +
                    "Default: ${DEFAULT-VALUE}\n" +
                    "'Backward' means new schema can be used to read data produced by earlier schema.\n" +
                    "'Forward' means data produced by new schema can be read by earlier schema.\n" +
                    "'Full' means both forward and backward compatible.\n" +
                    "'Transitive' means check for compatibility against all earlier schema versions, else just the previous one."
    ) CompatibilityLevel compatibilityLevel;

    @Option(names = {"-l", "--log-level"},
            defaultValue = "OFF",
            description = "Valid values: ${COMPLETION-CANDIDATES}\n" +
                    "Default: ${DEFAULT-VALUE}"
    ) LogLevel logLevel;

    @Parameters(index = "0")    File newSchemaFile;
    @Parameters(index = "1", arity = "1..*") File[] previousSchemaFiles;

    ParsedSchema newSchema;
    List<ParsedSchema> previousSchemas;

    Map<CompatibilityLevel, CompatibilityChecker> levelToChecker = Map.of(
            CompatibilityLevel.BACKWARD, CompatibilityChecker.BACKWARD_CHECKER,
            CompatibilityLevel.FORWARD, CompatibilityChecker.FORWARD_CHECKER,
            CompatibilityLevel.FULL, CompatibilityChecker.FULL_CHECKER,
            CompatibilityLevel.BACKWARD_TRANSITIVE, CompatibilityChecker.BACKWARD_TRANSITIVE_CHECKER,
            CompatibilityLevel.FORWARD_TRANSITIVE, CompatibilityChecker.FORWARD_TRANSITIVE_CHECKER,
            CompatibilityLevel.FULL_TRANSITIVE, CompatibilityChecker.FULL_TRANSITIVE_CHECKER
    );

    private static ParsedSchema loadSchema(File schemaFile) throws IOException
    {
        String content = Files.readString(schemaFile.toPath());
        JsonSchemaProvider provider = new JsonSchemaProvider();
        return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
    }

    private void loadSchemas() throws IOException {
        previousSchemas = new ArrayList<>();
        for (File existingSchemaFile : previousSchemaFiles) {
            ParsedSchema schema = loadSchema(existingSchemaFile);
            previousSchemas.add(schema);
        }
        newSchema = loadSchema(newSchemaFile);
    }

    private void configureRootLogger() {
        BasicConfigurator.configure();
        LogManager.getRootLogger().setLevel(Level.toLevel(logLevel.name()));
    }

    public List<String> getReport() throws IOException {
        loadSchemas();
        CompatibilityChecker checker = levelToChecker.get(compatibilityLevel);
        return checker.isCompatible(newSchema, previousSchemas);
    }

    @Override
    public Integer call() throws IOException {
        configureRootLogger();
        List<String> report = getReport();
        report.forEach(System.out::println);
        return report.size();
    }

    public static void main(String... args) {
        // java.io.IOException: Resource not found: "org/joda/time/tz/data/ZoneInfoMap"
        // https://github.com/dlew/joda-time-android/issues/148
        // https://gist.github.com/vaughandroid/99ce457e62f74ad9be2f794f014e3c8d
        DateTimeZone.setProvider(new UTCProvider());

        int exitCode = new CommandLine(new ChuckD()).execute(args);
        System.exit(exitCode);
    }
}
