package com.anentropic.chuckd;

public record SchemaIncompatibility(
    String type,       // enum name: "TYPE_NARROWED", "MISSING_UNION_BRANCH", etc.
    String path,       // "#/properties/age", "/fields/0/type/1", "#/Pet"
    String direction,  // "backward" or "forward"
    String message     // human-readable detail (Avro only, null for JSON Schema/Protobuf)
) {}
