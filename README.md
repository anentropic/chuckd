# chuckd

> A JSON Schema evolutions validator.

Borrowed from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli tool, so it could easily be extended to support Avro and Protobuf formats too.

Developed and tested against JDK 11 via Gradle.

### Usage

Just pass the paths of your schema files:
```
chuckd <latest schema> <prev schema> [<prev schema> ...]
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
$ app/build/bin/chuckd app/src/test/resources/person-1.1.0.json app/src/test/resources/person-1.0.0.json
Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}
```