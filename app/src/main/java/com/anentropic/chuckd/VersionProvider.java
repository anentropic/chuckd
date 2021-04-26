package com.anentropic.chuckd;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine;


public class VersionProvider implements CommandLine.IVersionProvider {

    Properties properties = new Properties();

    public VersionProvider() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("version.properties");
        if (inputStream != null) {
            properties.load(inputStream);
        } else {
            throw new FileNotFoundException("'version.properties' not found in the classpath");
        }
    }

    @Override
    public String[] getVersion() {
        return new String[] { properties.getProperty("version.semver") };
    }
}
