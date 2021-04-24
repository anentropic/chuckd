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

```
brew install gradle
brew install --cask graalvm/tap/graalvm-ce-java11
gradle nativeImage
```
