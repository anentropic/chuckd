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

@Command(name = "chuckd",
        mixinStandardHelpOptions = true,
        description = "Report evolution compatibility of latest vs existing schema versions.",
        versionProvider = ConfigProvider.class)
class ChuckD implements Callable<Integer> {

    @Option(names = {"-c", "--compatibility"},
            defaultValue = "FORWARD_TRANSITIVE",
            description = "Valid values: ${COMPLETION-CANDIDATES} Default: ${DEFAULT-VALUE}\n" +
                    "'Backward' means new schema can be used to read data produced by earlier schema.\n" +
                    "'Forward' means data produced by new schema can be read by earlier schema.\n" +
                    "'Full' means both forward and backward compatible.\n" +
                    "'Transitive' means all earlier schema versions, else just the previous one."
    ) CompatibilityLevel compatibilityLevel;

    @Parameters(index = "0")    File newSchemaFile;
    @Parameters(index = "1..*") File[] previousSchemaFiles;

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

    public List<String> getReport() throws IOException {
        loadSchemas();
        CompatibilityChecker checker = levelToChecker.get(compatibilityLevel);
        return checker.isCompatible(newSchema, previousSchemas);
    }

    @Override
    public Integer call() throws IOException {
        List<String> report = getReport();
        report.forEach(System.out::println);
        return report.size();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new ChuckD()).execute(args);
        System.exit(exitCode);
    }
}
