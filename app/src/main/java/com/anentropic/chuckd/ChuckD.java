package com.anentropic.chuckd;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.AbstractSchemaProvider;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.joda.time.DateTimeZone;
import org.joda.time.tz.UTCProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

enum SchemaFormat {
    JSONSCHEMA,
    AVRO,
    PROTOBUF,
};

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

enum OutputFormat {
    TEXT,
    JSON,
};

@Command(name = "chuckd",
        mixinStandardHelpOptions = true,
        description = "Report evolution compatibility of latest vs existing schema versions.",
        versionProvider = VersionProvider.class)
class ChuckD implements Callable<Integer> {

    @Option(names = {"-f", "--format"},
            defaultValue = "JSONSCHEMA",
            description = "Valid values: ${COMPLETION-CANDIDATES}\n" +
                    "Default: ${DEFAULT-VALUE}\n" +
                    "Format of schema versions being checked"
    ) SchemaFormat schemaFormat;

    @Option(names = {"-c", "--compatibility"},
            defaultValue = "FORWARD_TRANSITIVE",
            description = "Valid values: ${COMPLETION-CANDIDATES}\n" +
                    "Default: ${DEFAULT-VALUE}\n" +
                    "'Backward' means new schema can be used to read data produced by earlier schema.\n" +
                    "'Forward' means data produced by new schema can be read by earlier schema.\n" +
                    "'Full' means both forward and backward compatible.\n" +
                    "'Transitive' means check for compatibility against all earlier schema versions, else just the previous one."
    ) CompatibilityLevel compatibilityLevel;

    @Option(names = {"-o", "--output"},
            defaultValue = "TEXT",
            description = "Valid values: ${COMPLETION-CANDIDATES}\n" +
                    "Default: ${DEFAULT-VALUE}\n" +
                    "Output format for compatibility report"
    ) OutputFormat outputFormat;

    @Option(names = {"-l", "--log-level"},
            defaultValue = "OFF",
            description = "Valid values: ${COMPLETION-CANDIDATES}\n" +
                    "Default: ${DEFAULT-VALUE}"
    ) LogLevel logLevel;

    @Parameters(
            index = "0",
            description = "New version of the schema to compare for compatibility."
    ) File newSchemaFile;
    @Parameters(
            index = "1",
            arity = "1..*",
            description = "Previous version(s) of the schema in oldest->newest order."
    ) File[] previousSchemaFiles;

    ParsedSchema newSchema;
    List<ParsedSchema> previousSchemas;

    Map<SchemaFormat, Class<? extends AbstractSchemaProvider>> formatToProvider = Map.of(
            SchemaFormat.JSONSCHEMA, JsonSchemaProvider.class,
            SchemaFormat.AVRO, AvroSchemaProvider.class,
            SchemaFormat.PROTOBUF, ProtobufSchemaProvider.class
    );

    private static ParsedSchema loadSchema(AbstractSchemaProvider provider, File schemaFile) throws IOException
    {
        String content = Files.readString(schemaFile.toPath());
        return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
    }

    private void loadSchemas() throws IOException {
        AbstractSchemaProvider provider;
        try {
            provider = formatToProvider.get(schemaFormat).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }
        previousSchemas = new ArrayList<>();
        for (File existingSchemaFile : previousSchemaFiles) {
            ParsedSchema schema = loadSchema(provider, existingSchemaFile);
            previousSchemas.add(schema);
        }
        newSchema = loadSchema(provider, newSchemaFile);
    }

    private void configureRootLogger() {
        BasicConfigurator.configure();
        LogManager.getRootLogger().setLevel(Level.toLevel(logLevel.name()));
    }

    public List<SchemaIncompatibility> getStructuredReport() throws IOException {
        loadSchemas();
        CompatibilityReporter reporter = new CompatibilityReporter();
        return reporter.check(compatibilityLevel, newSchema, previousSchemas, schemaFormat);
    }

    static String formatText(List<SchemaIncompatibility> issues) {
        StringBuilder sb = new StringBuilder();
        for (SchemaIncompatibility issue : issues) {
            sb.append(issue.path()).append(": ").append(issue.type())
              .append(" (").append(issue.direction()).append(")");
            if (issue.message() != null) {
                sb.append(" - ").append(issue.message());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    static String formatJson(List<SchemaIncompatibility> issues) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(issues);
    }

    @Override
    public Integer call() throws IOException {
        configureRootLogger();
        List<SchemaIncompatibility> issues = getStructuredReport();
        if (!issues.isEmpty()) {
            String output = switch (outputFormat) {
                case TEXT -> formatText(issues);
                case JSON -> formatJson(issues);
            };
            System.out.print(output);
        }
        return issues.size();
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
