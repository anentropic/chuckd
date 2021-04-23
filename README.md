# chuckd

> A schema evolutions validator.

Borrowed from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli tool, so it supports JSON Schema, Avro and Protobuf formats.

Developed and tested against JDK 11 via Gradle.

### Usage

For now:

1. Install Gradle
2. Clone this repo and `cd` into it
3. `./gradlew run <path to latest schema> <path to prev schema>`


### Development

```
brew install gradle
brew install --cask graalvm/tap/graalvm-ce-java11
```
