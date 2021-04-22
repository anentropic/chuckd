package chuckd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import io.confluent.kafka.schemaregistry.CompatibilityChecker;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;

public class App {
    public static void main(String[] args) throws IOException
    {
        Path pathL = Path.of(args[0]);
        Path pathR = Path.of(args[1]);

        ParsedSchema schemaL = App.loadSchema(pathL);
        ParsedSchema schemaR = App.loadSchema(pathR);

        CompatibilityChecker checker = CompatibilityChecker.FULL_CHECKER;
        List<String> report = checker.isCompatible(schemaL, Collections.singletonList(schemaR));

        report.forEach(System.out::println);
    }

    public static ParsedSchema loadSchema(Path path) throws IOException
    {
        String content = Files.readString(path);
        JsonSchemaProvider provider = new JsonSchemaProvider();
        return provider.parseSchema(content, Collections.emptyList(), false).orElseThrow();
    }
}
