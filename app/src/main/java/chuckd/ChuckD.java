package chuckd;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;


@Command(name = "chuckd",
        mixinStandardHelpOptions = true,
        description = "Report evolution compatibility of latest vs existing schema versions.")
class ChuckD implements Callable<Integer> {

    @Parameters(index = "0")    File latestSchemaFile;
    @Parameters(index = "1..*") File[] existingSchemaFiles;

    ParsedSchema latestSchema;
    List<ParsedSchema> existingSchemas;

    private static ParsedSchema loadSchema(File schemaFile) throws IOException
    {
        String content = Files.readString(schemaFile.toPath());
        JsonSchemaProvider provider = new JsonSchemaProvider();
        return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
    }

    private void loadSchemas() throws IOException {
        existingSchemas = new ArrayList<>();
        for (File existingSchemaFile : existingSchemaFiles) {
            ParsedSchema schema = loadSchema(existingSchemaFile);
            existingSchemas.add(schema);
        }
        latestSchema = loadSchema(latestSchemaFile);
    }

    public List<String> getReport() throws IOException {
        loadSchemas();
        CompatibilityChecker checker = CompatibilityChecker.FULL_CHECKER;
        return checker.isCompatible(latestSchema, existingSchemas);
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
