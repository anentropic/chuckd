# chuckd

> Schema evolution validation tool.

![chuckd thug life](https://user-images.githubusercontent.com/147840/115955507-c4736280-a4ee-11eb-8638-8ac09e3b42f3.gif)

Borrowed from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli util. BYO schema registry, this just validates schema evolutions. At the moment only JSON Schema is implemented, but the Confluent registry supports Avro and Protobuf too, so they can be easily added.

Developed and tested against JDK 11 via Gradle.

## Usage

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

## Development

### Install pre-requisites
```
brew install gradle
brew install --cask graalvm/tap/graalvm-ce-java11
gu install native-image
```

### Build and test project
```
gradle build
```
...this compiles the project and runs the tests.

It also generates `app/build/scripts/app` shell script which wraps `java` + jar and should be runnable as if it was the `chuckd` cli tool, if your local `$JAVA_HOME` etc are set up correctly. (I didn't get it working, but I didn't try very hard...)

### Build the binary

Much slower to compile, but more appealing, we can use GraalVM to build a native image (which will be output in `app/build/bin/chuckd`)

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

#### Test the binary

Despite all the static typing in Java, it's still dynamic enough that you can compile a native image that craps out at runtime.

So we have some "smoke" tests that check you can perform basic operations with the binary.

We're using [BATS](https://bats-core.readthedocs.io/en/latest/) Bash test framework:
```
brew install bats-core
```

To run the tests:
```
bats bin-tests/smoke.bats
```

### Build the Docker image

To build the Docker image you need to configure 8 GB RAM for your docker daemon. Try less if you like, but I got errors with 4 GB, and I see around 6.5 GB reported when building locally. (This only applies to *building* the image from scratch, running it should have no special requirements).
```
docker build -t anentropic/chuckd .
```

Try it out:
```
docker run -v $(pwd)/app/src/test/resources:/schemas anentropic/chuckd person-1.1.0.json person-1.0.0.json
```

### TODOs:

- test `$ref` support is working
- add schema validation by default
- add Avro and Protobuf support
- Homebrew package
