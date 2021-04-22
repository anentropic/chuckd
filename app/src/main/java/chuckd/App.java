package chuckd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import org.jetbrains.annotations.NotNull;

public class App {
    public static void main(@NotNull String[] args) throws IOException {
        List<ParsedSchema> schemas = loadSchemas(args);
        List<String> report = getReport(schemas);
        report.forEach(System.out::println);
    }

    @NotNull
    public static List<ParsedSchema> loadSchemas(String[] args) throws IOException {
        List<ParsedSchema> schemas = new ArrayList<>();
        for (String arg : args) {
            ParsedSchema parsedSchema = App.loadSchema(Path.of(arg));
            schemas.add(parsedSchema);
        }
        return schemas;
    }

    public static List<String> getReport(List<ParsedSchema> schemas) {
        CompatibilityChecker checker = CompatibilityChecker.FULL_CHECKER;
        return checker.isCompatible(schemas.get(0), schemas.subList(1, schemas.size()));
    }

    public static ParsedSchema loadSchema(Path path) throws IOException
    {
        String content = Files.readString(path);
        JsonSchemaProvider provider = new JsonSchemaProvider();
        return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
    }
}
