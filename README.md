# chuckd

> Schema evolution validation tool.

![chuckd thug life](https://user-images.githubusercontent.com/147840/115955507-c4736280-a4ee-11eb-8638-8ac09e3b42f3.gif)

## What is it?

Let's say you are using schemas to define your API and message contracts between producer/consumer or client/backend. This unlocks various benefits around automated testing etc. When you roll out changes to a schema you need to ensure that deployed clients can understand both old and new schemas.

So you may want to take a "semantic versioning" approach where major incompatible changes require updates to client code, but minor backward-compatible changes are allowed. For the latter kind it's useful to have a tool to validate the compatibility properties of your evolving schema - **this is `chuckd`**.

The validation code is borrowed directly from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli util. The idea is you can "bring your own" registry (e.g. just a git repo) - and use this tool to validate schema evolutions via your CI/CD pipeline. Like CSR, `chuckd` supports JSON Schema, Avro and Protobuf schema formats.

Developed and tested against JDK 21, native image built with GraalVM. See [install](#install) for details.

### Example

In your message producer repo (e.g. api backend) you have your versioned schema files like:

```txt
schemas
  person
    current.json
    1.3.1.json
    1.3.0.json
    1.2.7.json
```

You can then use `chuckd` in your CI/CD to validate that `current.json` is backwards compatible with any previous versions of the schema still in use by clients/consumers.

(`chuckd` itself doesn't have any notion of semver filenames, it's up to you to configure your CI/CD to pass in the relevant files as args. See [usage](#usage) below.)

With **glob mode** you can also just point `chuckd` at a directory of versioned schema files:

```sh
chuckd "schemas/person.*.json"
```

Files are sorted with natural ordering (`v8, v9, v10` not `v10, v8, v9`), and the last match is treated as the latest schema.

### Contents

- [chuckd](#chuckd)
  - [What is it?](#what-is-it)
    - [Example](#example)
    - [Contents](#contents)
  - [Install](#install)
    - [Linux](#linux)
    - [Docker](#docker)
    - [macOS](#macos)
  - [Usage](#usage)
  - [Development](#development)
    - [Install pre-requisites](#install-pre-requisites)
    - [Build and test project](#build-and-test-project)
    - [Build the binary](#build-the-binary)
      - [Test the binary](#test-the-binary)
    - [Build the Docker image](#build-the-docker-image)
    - [TODOs](#todos)

## Install

### Linux

We have pre-built (via GraalVM native-image) binaries available at:  
https://github.com/anentropic/chuckd/releases

Releases include builds for:
- **x86_64** — `chuckd-Linux-x86_64-<version>.tar.gz`
- **aarch64 (ARM64)** — `chuckd-Linux-aarch64-<version>.tar.gz` _(since v0.6.0+)_

Just download, extract `chuckd` from the tar.gz, and move it to somewhere on your `$PATH`, e.g. `/usr/local/bin`.

### Docker

We have a multi-arch (amd64 / arm64) Docker image `anentropic/chuckd` available on Docker Hub:  
https://hub.docker.com/r/anentropic/chuckd/tags

This will be a convenient option in many cases, particularly in CI/CD systems.

These are also based on GraalVM native-image binaries.

### macOS

#### Homebrew

The easiest option for most macOS users:

```sh
brew install anentropic/tap/chuckd
```

#### Native binaries

We have pre-built (via GraalVM native-image) binaries available at:  
https://github.com/anentropic/chuckd/releases

Releases include builds for:
- **Apple Silicon (aarch64)** — `chuckd-macOS-aarch64-<version>.tar.gz` _(since v0.6.0+)_
- **Intel (x86_64)** — `chuckd-macOS-x86_64-<version>.tar.gz` _(prior to v0.6.0, discontinued)_

Just download, extract `chuckd` from the tar.gz, and move it to somewhere on your `$PATH`, e.g. `/usr/local/bin`.

**Note:** macOS may block downloaded binaries by default (Gatekeeper). If you see a security warning, see [these instructions](https://eshop.macsales.com/blog/57866-how-to-work-with-and-around-gatekeeper/) (scroll down to _"Opening Gatekeeper Blocked Apps"_) for how to allow it.

## Usage

`chuckd` has two modes:

### Explicit mode

Pass two or more schema file paths. The **last** argument is the new (latest) schema, all preceding arguments are previous versions:

```sh
chuckd [options] <previous...> <new>
```

```sh
chuckd schemas/person-1.0.0.json schemas/person-1.1.0.json
```

### Glob mode

Pass a single quoted glob pattern. Files are sorted with natural ordering (`v8, v9, v10` not `v10, v8, v9`) and the last match is treated as the latest schema:

```sh
chuckd [options] "schemas/person.*.json"
```

- If the glob matches 0 files: exit code 2 (usage error)
- If the glob matches 1 file: exit code 0 (trivially compatible)
- If the glob matches 2+ files: runs compatibility check

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | Compatible (or trivially compatible: single glob match) |
| 1 | Incompatible — breaking changes detected |
| 2 | Usage error — bad arguments, missing files, or glob matches nothing |
| 3 | Runtime error — file I/O failure or schema parse error |

### Output

- **TEXT mode** (default): no output on compatible schemas; incompatibility details on stdout if incompatible
- **JSON mode** (`--output JSON`): always produces valid JSON — `[]` on compatible, issue array on incompatible
- File metadata is printed to stderr by default. Use `--quiet` / `-q` to suppress it.

### Options

```txt
Usage: chuckd [-hqV] [-c=<compatibilityLevel>] [-f=<schemaFormat>]
              [-l=<logLevel>] [-o=<outputFormat>] <schemaArgs>...
Report evolution compatibility of latest vs existing schema versions.
      <schemaArgs>...   Glob mode (1 arg): pass a quoted glob pattern, e.g.
                          "schemas/person.*.json"
                        Explicit mode (2+ args): <previous...> <new> — last arg
                          is the new schema
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
  -f, --format=<schemaFormat>
                        Valid values: JSONSCHEMA, AVRO, PROTOBUF
                        Default: JSONSCHEMA
                        Format of schema versions being checked
  -h, --help            Show this help message and exit.
  -l, --log-level=<logLevel>
                        Valid values: OFF, ALL, DEBUG, INFO, WARN, ERROR, FATAL
                        Default: OFF
  -o, --output=<outputFormat>
                        Valid values: TEXT, JSON
                        Default: TEXT
  -q, --quiet           Suppress file metadata output on stderr
  -V, --version         Print version information and exit.

Exit codes:
  0   Compatible (or trivially compatible with a single glob match)
  1   Incompatible — breaking changes detected
  2   Usage error — bad arguments, missing files, or glob matches nothing
  3   Runtime error — file I/O failure or schema parse error
```

### Docker

For Docker the usage is essentially the same, but you need to mount a volume containing your schema files as `/schemas` in the container:

```sh
docker run -v /path/to/my/schemas:/schemas anentropic/chuckd person-1.0.0.json person-1.1.0.json
```

## Development

### Install pre-requisites

Install [GraalVM 21](https://www.graalvm.org/downloads/) (community edition is fine). The easiest way on macOS:

```sh
brew install --cask graalvm/tap/graalvm-community-jdk21
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-community-openjdk-21/Contents/Home
```

`native-image` is bundled with GraalVM — no separate installation step needed.

### Build and test project

```sh
gradle build
```

...this compiles the project and runs the tests.

It also generates `app/build/distributions/chuckd-x.y.z.zip`. If you unzip that then the `bin/chuckd` shell script it extracts is runnable - I guess it needs the adjacent `lib/` dir which was also extracted.

### Build the binary

Much slower to compile, but more appealing, we can use GraalVM to build a native image (which will be output in `app/build/native/nativeCompile/chuckd`)

```sh
gradle nativeCompile
```

Try it out:

```sh
[chuckd]$ app/build/native/nativeCompile/chuckd app/src/test/resources/person-1.0.0.json app/src/test/resources/person-1.1.0.json
Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}
[chuckd]$ echo $?
1
[chuckd]$ app/build/native/nativeCompile/chuckd --compatibility BACKWARD app/src/test/resources/person-1.0.0.json app/src/test/resources/person-1.1.0.json
[chuckd]$ echo $?
0
```

#### Test the binary

Despite all the static typing in Java, it's still dynamic enough that you can compile a native image that craps out at runtime.

So we have some "smoke" tests that check you can perform basic operations with the binary.

We're using [BATS](https://bats-core.readthedocs.io/en/latest/) Bash test framework:

```sh
brew install bats-core
```

To run the tests:

```sh
bats bat-tests/smoke.bats
```

### Build the Docker image

To build the Docker image you need to configure 8 GB RAM for your docker daemon. Try less if you like, but I got errors with 4 GB, and I see around 6.5 GB reported when building locally. (This only applies to *building* the image from scratch, running it should have no special requirements).

For a single-arch build:

```sh
docker build -t anentropic/chuckd .
```

Try it out:

```sh
docker run -v $(pwd)/app/src/test/resources:/schemas anentropic/chuckd person-1.0.0.json person-1.1.0.json
```
