# chuckd

> Schema evolution validation tool.

![chuckd thug life](https://user-images.githubusercontent.com/147840/115955507-c4736280-a4ee-11eb-8638-8ac09e3b42f3.gif)

Borrowed from [Confluent Schema Registry](https://github.com/confluentinc/schema-registry) and re-wrapped as a cli util. The idea is you can "bring your own" schema registry - and use this tool to validate schema evolutions. Like CSR, chuckd supports JSON Schema, Avro and Protobuf schema formats.

Developed and tested against JDK 11, native image built with GraalVM.

- [chuckd](#chuckd)
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

We have pre-built binaries available for x86-64 Linux, found at:  
https://github.com/anentropic/chuckd/releases

Just download, extract `chuckd` from the tar.gz, and move it to somewhere on your `$PATH`, e.g. `/usr/local/bin`.

### Docker

We have a multi-arch (amd64 / arm64) Docker image `anentropic/chuckd` available on Docker Hub:  
https://hub.docker.com/r/anentropic/chuckd/tags

This will be the easiest option in many cases, particularly for macOS users.

### macOS

We do have pre-built binaries for x86\_64 macOS, **but** by default they will be blocked from running by Gatekeeper. If you want to go this route, see [instructions here](https://eshop.macsales.com/blog/57866-how-to-work-with-and-around-gatekeeper/) (scroll down to _"Opening Gatekeeper Blocked Apps"_) for how to make it usable.

It seems like the Intel binary will run fine on Apple Silicon (arm64) macs after unblocking (I have tried it on my M1 macbook), but you might need to prepend `arch -x86_64` the first time you run it.

We also have a Homebrew tap... This _should_ have been the easiest option.

For **Catalina** users we are able to build a `bottle` (pre-built binary) so you should be able to just `brew install anentropic/tap/chuckd` as intended. As soon as GitHub Actions provides us with Big Sur runners we will start building bottles for Big Sur too.

Unfortunately if there is no bottle available (all versions of macOS other than Catalina currently) then we have to build from source, and this means the GraalVM native-image builder toolchain has to be set up manually first:

1. `brew install --cask graalvm/tap/graalvm-ce-java11`
2. follow the post-install instructions to configure your `JAVA_HOME` env and add the GraalVM bin dir to your `PATH` (see https://github.com/graalvm/homebrew-tap for more details)
3. `gu install native-image`

After all that, you should be able to `brew install anentropic/tap/chuckd`.

## Usage

Just pass the paths of two or more schema files:

```sh
chuckd <latest schema> <prev schema> [<prev schema> ...]
```

- the new schema version should be left-most, followed by previous versions of the schema to check against, in oldest->newest order.
- the files should all be versions of the _same_ schema
- no output (exit: `0`) means the versions are compatible
- if they are incompatible a non-zero exit code will be returned, and some info about the problem is printed like:  `Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}`

```txt
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
  -f, --format=<schemaFormat>
                        Valid values: JSONSCHEMA, AVRO, PROTOBUF
                        Default: JSONSCHEMA
                        Format of schema versions being checked
  -h, --help            Show this help message and exit.
  -l, --log-level=<logLevel>
                        Valid values: OFF, ALL, DEBUG, INFO, WARN, ERROR, FATAL
                        Default: OFF
  -V, --version         Print version information and exit.
```

For Docker the usage is essentially the same, but you need to mount a volume containing your schema files as `/schemas` in the container:

```sh
docker run -v /path/to/my/schemas:/schemas anentropic/chuckd person-1.1.0.json person-1.0.0.json
```

## Development

### Install pre-requisites

```sh
brew install gradle
brew install --cask graalvm/tap/graalvm-ce-java11
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java11-21.1.0/Contents/Home
export GRAALHOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java11-21.1.0/Contents/Home
$GRAALHOME/bin/gu install native-image
```

### Build and test project

```sh
gradle build
```

...this compiles the project and runs the tests.

It also generates `app/build/scripts/app` shell script which wraps `java` + jar and should be runnable as if it was the `chuckd` cli tool, if your local `$JAVA_HOME` etc are set up correctly. (I didn't get it working, but I didn't try very hard...)

### Build the binary

Much slower to compile, but more appealing, we can use GraalVM to build a native image (which will be output in `app/build/bin/chuckd`)

```sh
gradle nativeImage
```

Try it out:

```sh
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
docker run -v $(pwd)/app/src/test/resources:/schemas anentropic/chuckd person-1.1.0.json person-1.0.0.json
```

### TODOs

- maybe parse and normalise the diff strings returned by confluent lib
- add JTD support
