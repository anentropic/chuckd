# chuckd

> Schema evolution validation tool.

![chuckd thug life](https://user-images.githubusercontent.com/147840/115955507-c4736280-a4ee-11eb-8638-8ac09e3b42f3.gif)

Borrowed from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli util. BYO schema registry, this just validates schema evolutions. At the moment only JSON Schema is implemented, but the Confluent registry supports Avro and Protobuf too, so they can be easily added.

Developed and tested against JDK 11 via Gradle.

### Usage

Just pass the paths of your schema files:
```
chuckd <latest schema> <prev schema> [<prev schema> ...]
```

```
chuckd --help
Usage: chuckd [-hV] [-c=<compatibilityLevel>] [-l=<logLevel>] <newSchemaFile>
              <previousSchemaFiles>...
Report evolution compatibility of latest vs existing schema versions.
      <newSchemaFile>
      <previousSchemaFiles>...

  -c, --compatibility=<compatibilityLevel>
                        Valid values: BACKWARD, FORWARD, FULL,
                          BACKWARD_TRANSITIVE, FORWARD_TRANSITIVE,
                          FULL_TRANSITIVE
                        Default: FORWARD_TRANSITIVE
                        'Backward' means new schema can be used to read data
                          produced by earlier schema.
                        'Forward' means data produced by new schema can be read
                          by earlier schema.
                        'Full' means both forward and backward compatible.
                        'Transitive' means check for compatibility against all
                          earlier schema versions, else just the previous one.
  -h, --help            Show this help message and exit.
  -l, --log-level=<logLevel>
                        Valid values: OFF, ALL, DEBUG, INFO, WARN, ERROR, FATAL
                        Default: OFF
  -V, --version         Print version information and exit.
```

### Development

Install deps:
```
brew install gradle
brew install --cask graalvm/tap/graalvm-ce-java11
```

Build the executable locally:
```
gradle nativeImage
```

Try it out:
```
[chuckd]$ app/build/bin/chuckd app/src/test/resources/person-1.1.0.json app/src/test/resources/person-1.0.0.json
Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}
[chuckd]$ echo $?
1
[chuckd]$ app/build/bin/chuckd --compatibility BACKWARD app/src/test/resources/person-1.1.0.json app/src/test/resources/person-1.0.0.json
[chuckd]$ echo $?
0
```

To build the Docker image you need to configure 8GB RAM for your docker daemon. Try less if you like but I got errors with 4GB and I see around 6.5GB reported when building locally. (This only applies to *building* the image from scratch, running it should have no special requirements).
```
docker build -t anentropic/chuckd .
```

Try it out:
```
docker run -v $(pwd)/app/src/test/resources:/schemas anentropic/chuckd person-1.1.0.json person-1.0.0.json
```

#### TODOs:

- test `$ref` support is working
- add schema validation
- add Avro and Protobufs
- Homebrew package
- automate release builds (GitHub Actions)
