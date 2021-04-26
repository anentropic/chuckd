# chuckd

This image runs the `chuckd` cli tool, for checking backward compatibility of changes to your JSON Schema files.

See https://github.com/anentropic/chuckd for details.

### Usage

To use this image:
- mount the dir containing your schema files as `/schemas` in the container
- pass the filenames of two or more schema versions
- the new schema version being validated should be left-most, followed by earlier versions
- (the files should all be versions of the same schema)
- no output (exit: `0`) means the versions are compatible
- if they are incompatible a non-zero exit code will be returned, and some info about the problem is printed like:  `Found incompatible change: Difference{jsonPath='#/properties/age', type=TYPE_NARROWED}`

e.g.
```
docker run -v $(pwd)/app/src/test/resources:/schemas anentropic/chuckd person-1.1.0.json person-1.0.0.json
```

You can configure the compatibility level via `-c` as detailed below:

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
