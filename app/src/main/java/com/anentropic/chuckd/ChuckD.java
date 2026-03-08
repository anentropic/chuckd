package com.anentropic.chuckd;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        exitCodeOnInvalidInput = 2,
        exitCodeOnExecutionException = 3,
        description = "Report evolution compatibility of latest vs existing schema versions.",
        versionProvider = VersionProvider.class,
        footer = {
            "",
            "Exit codes:",
            "  0   Compatible (or trivially compatible with a single glob match)",
            "  1   Incompatible -- breaking changes detected",
            "  2   Usage error -- bad arguments, missing files, or glob matches nothing",
            "  3   Runtime error -- file I/O failure or schema parse error"
        })
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

    @Option(names = {"-q", "--quiet"},
            description = "Suppress file metadata output on stderr"
    ) boolean quiet;

    @Parameters(
            arity = "1..*",
            description = "Glob mode (1 arg): pass a quoted glob pattern, e.g. \"schemas/person.*.json\"\n" +
                          "Explicit mode (2+ args): <previous...> <new> -- last arg is the new schema"
    ) List<String> schemaArgs;

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

    private void loadSchemas(Path newSchemaPath, List<Path> previousPaths) throws IOException {
        AbstractSchemaProvider provider;
        try {
            provider = formatToProvider.get(schemaFormat).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to instantiate schema provider", e);
        }
        previousSchemas = new ArrayList<>();
        for (Path previousPath : previousPaths) {
            ParsedSchema schema = loadSchema(provider, previousPath.toFile());
            previousSchemas.add(schema);
        }
        newSchema = loadSchema(provider, newSchemaPath.toFile());
    }

    private void configureRootLogger() {
        BasicConfigurator.configure();
        LogManager.getRootLogger().setLevel(Level.toLevel(logLevel.name()));
    }

    /**
     * Expand a glob pattern to a naturally-sorted list of matching paths.
     * The parent component of the pattern becomes the search root (defaults to "." if absent).
     * Matching is done against file names only (not full paths).
     */
    List<Path> expandGlob(String pattern) throws IOException {
        Path patternPath = Path.of(pattern);
        Path searchRoot = patternPath.getParent() != null ? patternPath.getParent() : Path.of(".");
        String globPattern = "glob:" + patternPath.getFileName().toString();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

        if (!Files.exists(searchRoot)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.walk(searchRoot, 1)) {
            return stream
                .filter(p -> !Files.isDirectory(p))
                .filter(p -> matcher.matches(p.getFileName()))
                .sorted((a, b) -> naturalCompare(a.getFileName().toString(), b.getFileName().toString()))
                .collect(Collectors.toList());
        }
    }

    /**
     * Compare two strings using natural sort order: numeric chunks are compared as long values,
     * non-numeric chunks are compared lexicographically.
     * Examples: "v8" < "v9" < "v10", "file1" < "file2" < "file10"
     */
    static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startI = i, startJ = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                long na = Long.parseLong(a.substring(startI, i));
                long nb = Long.parseLong(b.substring(startJ, j));
                if (na != nb) return Long.compare(na, nb);
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++;
                j++;
            }
        }
        return a.length() - b.length();
    }

    public List<SchemaIncompatibility> getStructuredReport(Path newSchemaPath, List<Path> previousPaths) throws IOException {
        loadSchemas(newSchemaPath, previousPaths);
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
        if (issues.isEmpty()) {
            return "[]";
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(issues);
    }

    @Override
    public Integer call() throws IOException {
        configureRootLogger();

        List<Path> schemaPaths;
        if (schemaArgs.size() == 1) {
            // Glob mode: expand the single argument as a glob pattern
            schemaPaths = expandGlob(schemaArgs.get(0));
            if (schemaPaths.isEmpty()) {
                System.err.println("No files matched pattern: " + schemaArgs.get(0));
                return 2;
            }
            if (schemaPaths.size() == 1) {
                if (!quiet) {
                    System.err.println("Single match, trivially compatible: " + schemaPaths.get(0));
                }
                return 0;
            }
        } else {
            // Explicit mode: 2+ args, last is new schema, rest are previous
            schemaPaths = schemaArgs.stream().map(Path::of).collect(Collectors.toList());
        }

        // Last path = new schema; all prior = previous schemas (oldest to newest)
        Path newSchemaPath = schemaPaths.get(schemaPaths.size() - 1);
        List<Path> previousPaths = schemaPaths.subList(0, schemaPaths.size() - 1);

        if (!quiet) {
            System.err.println("Previous: " + previousPaths.stream()
                .map(Path::toString).collect(Collectors.joining(", ")));
            System.err.println("New:      " + newSchemaPath);
        }

        List<SchemaIncompatibility> issues = getStructuredReport(newSchemaPath, previousPaths);

        if (outputFormat == OutputFormat.JSON) {
            System.out.println(formatJson(issues));
        } else if (!issues.isEmpty()) {
            System.out.print(formatText(issues));
        }

        return issues.isEmpty() ? 0 : 1;
    }

    public static void main(String... args) {
        // java.io.IOException: Resource not found: "org/joda/time/tz/data/ZoneInfoMap"
        // https://github.com/dlew/joda-time-android/issues/148
        // https://gist.github.com/vaughandroid/99ce457e62f74ad9be2f794f014e3c8d
        DateTimeZone.setProvider(new UTCProvider());

        int exitCode = new CommandLine(new ChuckD())
            .setExitCodeExceptionMapper(t -> {
                if (t instanceof CommandLine.ParameterException) return 2;
                return 3;
            })
            .execute(args);
        System.exit(exitCode);
    }
}
