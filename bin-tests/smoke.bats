#!/usr/bin/env bats

#  basic "smoke tests" that the native image doesn't blow up at run-time

bin_path="app/build/bin"
res_path="app/src/test/resources"

@test "--help" {
  run ${bin_path}/chuckd --help
  [ "$status" -eq 0 ]
  [[ "${lines[0]}" = "Usage: chuckd"* ]]
}

@test "compatible schemas -> returns exit 0" {
  run ${bin_path}/chuckd -c BACKWARD ${res_path}/person-1.1.0.json ${res_path}/person-1.0.0.json
  [ "$status" -eq 0 ]
  [ "$output" = "" ]
}

@test "incompatible schemas -> returns exit >0 and error" {
  run ${bin_path}/chuckd -c FORWARD ${res_path}/person-1.1.0.json ${res_path}/person-1.0.0.json
  [ "$status" -eq 1 ]
  [[ "$output" = "Found incompatible change:"* ]]
}
