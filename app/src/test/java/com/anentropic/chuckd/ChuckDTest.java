package com.anentropic.chuckd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;


public class ChuckDTest {
    Path resourcesPath = Paths.get("src","test", "resources");

    ChuckD app;
    CommandLine cmd;

    @Before
    public void setUp() {
        app = new ChuckD();
        cmd = new CommandLine(app);
    }

    @Test public void testAppGetReport() throws IOException {
        String arg0 = resourcesPath.resolve("person-1.0.0.json").toFile().getAbsolutePath();
        String arg1 = resourcesPath.resolve("person-1.1.0.json").toFile().getAbsolutePath();
        String[] args = {"-c", "FULL", arg0, arg1};
        cmd.parseArgs(args);

        List<String> report = app.getReport();

        assertEquals(report.size(), 1);
    }
}
