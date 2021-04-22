package chuckd;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

public class AppTest {
    Path resourcesPath = Paths.get("src","test", "resources");

    @Test public void testAppGetReport() throws IOException {
        String arg0 = resourcesPath.resolve("person-1.0.0.json").toFile().getAbsolutePath();
        String arg1 = resourcesPath.resolve("person-1.1.0.json").toFile().getAbsolutePath();

        String[] args = {arg0, arg1};

        List<ParsedSchema> schemas = App.loadSchemas(args);
        List<String> report = App.getReport(schemas);

        assertEquals(report.size(), 1);
    }
}
