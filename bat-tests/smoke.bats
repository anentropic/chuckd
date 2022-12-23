#!/usr/bin/env bats

#  basic "smoke tests" that the native image doesn't blow up at run-time

bin_path="${CHUCKD_BIN_PATH:-app/build/bin}"
res_path="${CHUCKD_RES_PATH:-app/src/test/resources}"

# brew test sets this, seems unnecessary and causes extra cruft in output
unset _JAVA_OPTIONS

@test "--help" {
  run ${bin_path}/chuckd --help
  [ "$status" -eq 0 ]
  [[ "${lines[0]}" = "Usage: chuckd"* ]]
}

@test "compatible JSON schemas -> returns exit 0" {
  run ${bin_path}/chuckd -c BACKWARD ${res_path}/jsonschema/person-base.json ${res_path}/jsonschema/person-narrowed.json
  [ "$status" -eq 0 ]
  [ "$output" = "" ]
}

@test "incompatible JSON schemas -> returns exit >0 and error" {
  run ${bin_path}/chuckd -c FORWARD ${res_path}/jsonschema/person-base.json ${res_path}/jsonschema/person-narrowed.json
  [ "$status" -eq 1 ]
  [[ "$output" = "Found incompatible change:"* ]]
}

@test "compatible Avro schemas -> returns exit 0" {
  run ${bin_path}/chuckd -f AVRO -c BACKWARD ${res_path}/avro/person-base.avsc ${res_path}/avro/person-narrowed.avsc
  [ "$status" -eq 0 ]
  [ "$output" = "" ]
}

@test "incompatible Avro schemas -> returns exit >0 and error" {
  run ${bin_path}/chuckd -f AVRO -c FORWARD ${res_path}/avro/person-base.avsc ${res_path}/avro/person-narrowed.avsc
  [ "$status" -eq 1 ]
  [[ "$output" = "Incompatibility"* ]]
}
