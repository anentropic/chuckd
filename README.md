# chuckd

> A JSON Schema evolutions validator.

![chuckd thug life](https://user-images.githubusercontent.com/147840/115955507-c4736280-a4ee-11eb-8638-8ac09e3b42f3.gif)

Borrowed from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli tool, so it could easily be extended to support Avro and Protobuf formats too.

Developed and tested against JDK 11 via Gradle.

### Usage

Just pass the paths of your schema files:
```
chuckd <latest schema> <prev schema> [<prev schema> ...]
```

```
chuckd --help
Usage: chuckd [-hV] [-c=<compatibilityLevel>] <newSchemaFile>
              [<previousSchemaFiles>...]
Report evolution compatibility of latest vs existing schema versions.
      <newSchemaFile>
      [<previousSchemaFiles>...]

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
                        'Transitive' means all earlier schema versions, else
                          just the previous one.
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.
```

### Development

Install deps:
```
brew install gradle
brew install --cask graalvm/tap/graalvm-ce-java11
```

Build the executable:
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

#### TODOs:

- maybe auto-set 'transitivity' based on num of prev schemas passed in 
- add schema validation
- add Avro and Protobufs
- Dockerised build
- Homebrew package
