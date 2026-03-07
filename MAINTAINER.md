# Maintainer Guide

## Prerequisites

- **JDK 21** (GraalVM distribution recommended for native image builds)
- **Gradle 9.4** (provided via wrapper, no manual install needed)

### Install SDKMAN Java environment manager

```sh
curl -s "https://get.sdkman.io" | bash
```

### Install GraalVM JDK 21 on macOS

```sh
# sdkman
sdk install java 21-graalce
sdk use java 21-graalce

# see available versions
sdk list java
```

Or else:

```sh
# via Homebrew
brew install --cask graalvm-jdk
# or download from https://www.graalvm.org/downloads/

# set as current version in shell
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home
```

Verify:

```sh
java -version
# Expected: java 21.x.x ... Oracle GraalVM ...
```

## Build & Test

All commands use the Gradle wrapper (`./gradlew`) — no global Gradle install required.

### Run tests

```sh
./gradlew test --rerun
```

(`rerun` flag ensures you see output if tests were already built and run previously)

Test report: `app/build/reports/tests/test/index.html`

### Build (compile + test)

```sh
./gradlew build
```

### Run from source

```sh
./gradlew run --args='-c FORWARD path/to/new.json path/to/old.json'
```

### Build fat JAR

```sh
./gradlew shadowJar
```

Output: `app/build/libs/chuckd-all.jar`

Run it:

```sh
java -jar app/build/libs/chuckd-all.jar --help
```

### Build GraalVM native image

```sh
./gradlew nativeCompile
```

Output: `app/build/native/nativeCompile/chuckd`

### Smoke-test native image

Requires [bats-core](https://github.com/bats-core/bats-core):

```sh
brew install bats-core
bats bat-tests/smoke.bats
```

## Versioning

Semantic versioning is managed by the [gradle-semver](https://github.com/ethauvin/semver-gradle) plugin. The version lives in `app/src/main/resources/version.properties`.

### Bump version

```sh
./gradlew incrementPatch    # 0.6.0 -> 0.6.1
./gradlew incrementMinor    # 0.6.0 -> 0.7.0
./gradlew incrementMajor    # 0.6.0 -> 1.0.0
```

Or set an exact version:

```sh
./gradlew -Dversion.semver=1.0.0-beta
```

## Release Process

Releases are fully automated via GitHub Actions:

1. Bump the version (see above) and commit the changed `version.properties`
2. Push/merge to `main`
3. **detect-release** workflow detects the version change and pushes a git tag
4. **build-release** workflow triggers on the new tag and:
   - Builds native images for macOS (aarch64), Linux (x86_64), Linux (aarch64)
   - Creates a GitHub release with `.tar.gz` artifacts
   - Builds and pushes a multi-arch Docker image to `anentropic/chuckd` on Docker Hub
5. **homebrew** workflow updates the formula in `anentropic/homebrew-tap`

### CI Workflows

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `test-build.yml` | Pull requests | Runs `./gradlew build` + native image smoke tests |
| `detect-release.yml` | Push to `main` (version.properties changed) | Tags the commit with the version |
| `build-release.yml` | Version tag pushed | Builds native images, Docker image, creates GitHub release |
| `homebrew.yml` | GitHub release published | Updates Homebrew tap formula |
| `weekly-build.yml` | Monday 9am UTC | Builds against latest deps, opens issue on failure |

## Project Structure

```
app/
├── src/main/java/com/anentropic/chuckd/
│   ├── ChuckD.java                 # CLI entry point (picocli)
│   ├── CompatibilityReporter.java  # Schema compatibility checking
│   ├── SchemaIncompatibility.java  # Structured incompatibility record
│   └── VersionProvider.java        # Reads version from properties
├── src/main/resources/
│   ├── reflection.json             # GraalVM native-image reflection config
│   └── version.properties          # Semantic version
├── src/test/java/com/anentropic/chuckd/
│   ├── ChuckDTestBase.java         # Shared test infrastructure
│   ├── ChuckDTestJSONSchema.java   # JSON Schema integration tests
│   ├── ChuckDTestAvro.java         # Avro integration tests
│   ├── ChuckDTestProtobuf.java     # Protobuf integration tests
│   └── CompatibilityReporterTest.java  # Reporter unit tests
└── src/test/resources/             # Test schema files
    ├── jsonschema/
    ├── avro/
    └── protobuf/
bat-tests/
└── smoke.bats                      # Native image smoke tests
Dockerfile                          # Multi-stage native image build
```

## GraalVM Native Image Notes

The native image build requires reflection configuration (`app/src/main/resources/reflection.json`) because Confluent schema providers are instantiated via reflection. If you add new classes that need reflective access at runtime, add them to this file.

Key native image settings (in `app/build.gradle` under `graalvmNative`):

- `--enable-url-protocols=https` — needed for JSON Schema `$ref` resolution
- `--initialize-at-build-time=org.apache.log4j` — log4j must initialize at build time
- `-H:IncludeResources=version.properties` — bundles version info into the image
- `-H:IncludeResources=metaschemas/.*` and `json-meta-schemas/.*` — JSON Schema metaschema resources

## Docker

Build locally:

```sh
docker build -t chuckd .
```

The Dockerfile uses a multi-stage build: GraalVM native image compiled with `--static`, then copied into a `scratch` image (no OS layer).

Run:

```sh
docker run -v $(pwd)/schemas:/schemas chuckd -c FORWARD new.json old.json
```
