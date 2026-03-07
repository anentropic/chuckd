package com.anentropic.chuckd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityResult;
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility;

class CompatibilityReporter {

    public List<SchemaIncompatibility> check(
            CompatibilityLevel level,
            ParsedSchema newSchema,
            List<ParsedSchema> previousSchemas,
            SchemaFormat format
    ) {
        boolean transitive = level.name().contains("TRANSITIVE");
        boolean backward = level.name().contains("BACKWARD") || level.name().contains("FULL");
        boolean forward = level.name().contains("FORWARD") || level.name().contains("FULL");

        // Non-transitive: check only the last (most recent) previous schema
        // Transitive: check all previous schemas, most recent first, fail-fast
        List<ParsedSchema> toCheck;
        if (transitive) {
            toCheck = new ArrayList<>(previousSchemas);
            Collections.reverse(toCheck);
        } else {
            toCheck = List.of(previousSchemas.get(previousSchemas.size() - 1));
        }

        for (ParsedSchema oldSchema : toCheck) {
            List<SchemaIncompatibility> issues = new ArrayList<>();

            if (backward) {
                issues.addAll(comparePair(format, newSchema, oldSchema, "backward"));
            }
            if (forward) {
                issues.addAll(comparePair(format, oldSchema, newSchema, "forward"));
            }

            if (!issues.isEmpty()) {
                return issues;
            }
        }

        return List.of();
    }

    private List<SchemaIncompatibility> comparePair(
            SchemaFormat format,
            ParsedSchema reader,
            ParsedSchema writer,
            String direction
    ) {
        return switch (format) {
            case JSONSCHEMA -> compareJsonSchemas(
                    (JsonSchema) reader, (JsonSchema) writer, direction);
            case AVRO -> compareAvroSchemas(
                    (AvroSchema) reader, (AvroSchema) writer, direction);
            case PROTOBUF -> compareProtobufSchemas(
                    (ProtobufSchema) reader, (ProtobufSchema) writer, direction);
        };
    }

    private List<SchemaIncompatibility> compareJsonSchemas(
            JsonSchema reader, JsonSchema writer, String direction
    ) {
        // JSON Schema diff: compare(original, update) finds changes from original -> update
        // For backward: reader=new, writer=old -- new can read old
        //   We want diffs where old->new introduces incompatible changes for a reader of old data
        //   compare(old, new) = compare(writer, reader)
        // For forward: reader=old, writer=new -- old can read new
        //   compare(new, old) = compare(writer, reader)
        // In both cases the caller has already swapped reader/writer appropriately,
        // so we compare(writer, reader) which is compare(original, update)
        List<io.confluent.kafka.schemaregistry.json.diff.Difference> diffs =
                io.confluent.kafka.schemaregistry.json.diff.SchemaDiff.compare(
                        writer.rawSchema(), reader.rawSchema());

        Set<io.confluent.kafka.schemaregistry.json.diff.Difference.Type> compatibleChanges =
                io.confluent.kafka.schemaregistry.json.diff.SchemaDiff.COMPATIBLE_CHANGES;

        List<SchemaIncompatibility> results = new ArrayList<>();
        for (io.confluent.kafka.schemaregistry.json.diff.Difference diff : diffs) {
            if (!compatibleChanges.contains(diff.getType())) {
                results.add(new SchemaIncompatibility(
                        diff.getType().name(),
                        diff.getJsonPath(),
                        direction,
                        null
                ));
            }
        }
        return results;
    }

    private List<SchemaIncompatibility> compareAvroSchemas(
            AvroSchema reader, AvroSchema writer, String direction
    ) {
        SchemaPairCompatibility compatibility =
                SchemaCompatibility.checkReaderWriterCompatibility(
                        reader.rawSchema(), writer.rawSchema());

        SchemaCompatibilityResult result = compatibility.getResult();
        List<SchemaIncompatibility> results = new ArrayList<>();

        for (SchemaCompatibility.Incompatibility incompat : result.getIncompatibilities()) {
            results.add(new SchemaIncompatibility(
                    incompat.getType().name(),
                    incompat.getLocation(),
                    direction,
                    incompat.getMessage()
            ));
        }
        return results;
    }

    private List<SchemaIncompatibility> compareProtobufSchemas(
            ProtobufSchema reader, ProtobufSchema writer, String direction
    ) {
        // Protobuf diff: compare(original, update)
        // Same argument ordering logic as JSON Schema
        List<io.confluent.kafka.schemaregistry.protobuf.diff.Difference> diffs =
                io.confluent.kafka.schemaregistry.protobuf.diff.SchemaDiff.compare(
                        writer, reader);

        Set<io.confluent.kafka.schemaregistry.protobuf.diff.Difference.Type> compatibleChanges =
                io.confluent.kafka.schemaregistry.protobuf.diff.SchemaDiff.COMPATIBLE_CHANGES;

        List<SchemaIncompatibility> results = new ArrayList<>();
        for (io.confluent.kafka.schemaregistry.protobuf.diff.Difference diff : diffs) {
            if (!compatibleChanges.contains(diff.getType())) {
                results.add(new SchemaIncompatibility(
                        diff.getType().name(),
                        diff.getFullPath(),
                        direction,
                        null
                ));
            }
        }
        return results;
    }
}
