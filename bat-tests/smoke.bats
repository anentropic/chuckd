#!/usr/bin/env bats

#  basic "smoke tests" that the native image doesn't blow up at run-time

bin_path="${CHUCKD_BIN_PATH:-app/build/native/nativeCompile}"
res_path="${CHUCKD_RES_PATH:-app/src/test/resources}"

# brew test sets this, seems unnecessary and causes extra cruft in output
unset _JAVA_OPTIONS

@test "--help" {
  run "${bin_path}/chuckd" --help
  [ "$status" -eq 0 ]
  [[ "${lines[0]}" = "Usage: chuckd"* ]]
}

@test "compatible JSON schemas -> returns exit 0" {
  run "${bin_path}/chuckd" -c BACKWARD "${res_path}/jsonschema/person-narrowed.json" "${res_path}/jsonschema/person-base.json"
  [ "$status" -eq 0 ]
}

@test "incompatible JSON schemas -> returns exit 1 and error" {
  run "${bin_path}/chuckd" -c FORWARD "${res_path}/jsonschema/person-narrowed.json" "${res_path}/jsonschema/person-base.json"
  [ "$status" -eq 1 ]
  [[ "$output" = *"MAX_LENGTH_ADDED"* ]]
}

@test "compatible Avro schemas -> returns exit 0" {
  run "${bin_path}/chuckd" -f AVRO -c BACKWARD "${res_path}/avro/person-narrowed.avsc" "${res_path}/avro/person-base.avsc"
  [ "$status" -eq 0 ]
}

@test "incompatible Avro schemas -> returns exit 1 and error" {
  run "${bin_path}/chuckd" -f AVRO -c FORWARD "${res_path}/avro/person-narrowed.avsc" "${res_path}/avro/person-base.avsc"
  [ "$status" -eq 1 ]
  [[ "$output" = *"MISSING_UNION_BRANCH"* ]]
}

@test "bad flag -> exit 2" {
  run "${bin_path}/chuckd" --nonexistent-flag
  [ "$status" -eq 2 ]
}

@test "missing file -> exit 3" {
  run "${bin_path}/chuckd" "nonexistent-file-1.json" "nonexistent-file-2.json"
  [ "$status" -eq 3 ]
}

@test "glob mode, no matches -> exit 2" {
  run "${bin_path}/chuckd" "nonexistent-dir/*.json"
  [ "$status" -eq 2 ]
  [[ "$output" = *"No files matched"* ]] || [[ "${lines[0]}" = *"No files matched"* ]]
}

@test "glob mode, single match -> exit 0" {
  tmpdir=$(mktemp -d)
  cp "${res_path}/jsonschema/person-base.json" "${tmpdir}/schema-v1.json"
  run "${bin_path}/chuckd" "${tmpdir}/schema-v1.json"
  rm -rf "${tmpdir}"
  [ "$status" -eq 0 ]
}

@test "glob mode, two matches -> runs comparison" {
  tmpdir=$(mktemp -d)
  cp "${res_path}/jsonschema/person-narrowed.json" "${tmpdir}/person-v1.json"
  cp "${res_path}/jsonschema/person-base.json" "${tmpdir}/person-v2.json"
  run "${bin_path}/chuckd" -c BACKWARD "${tmpdir}/person-*.json"
  rm -rf "${tmpdir}"
  [ "$status" -eq 0 ]
}

@test "quiet mode suppresses stderr metadata" {
  run "${bin_path}/chuckd" -q -c BACKWARD "${res_path}/jsonschema/person-narrowed.json" "${res_path}/jsonschema/person-base.json"
  [ "$status" -eq 0 ]
  [ "$output" = "" ]
}

@test "JSON output on compatible schemas produces []" {
  run "${bin_path}/chuckd" -q -o JSON -c BACKWARD "${res_path}/jsonschema/person-narrowed.json" "${res_path}/jsonschema/person-base.json"
  [ "$status" -eq 0 ]
  [[ "$output" = *"[]"* ]]
}
