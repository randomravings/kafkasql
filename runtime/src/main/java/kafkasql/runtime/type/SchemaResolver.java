package kafkasql.runtime.type;

import kafkasql.runtime.value.StructValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves schema drift between the wire (server) and the current (client) schema.
 *
 * <h3>Server schema</h3>
 * The cumulative type definition after all CREATEs and ALTERs. Fields may be
 * active or dropped.
 *
 * <h3>Write resolution</h3>
 * Given a client-provided {@link StructValue} and the current {@link StructType}:
 * <ul>
 *   <li>Dropped fields in the value → rejected (cannot write to dropped fields)</li>
 *   <li>Active fields missing from the value → filled from defaults or null</li>
 * </ul>
 *
 * <h3>Read resolution</h3>
 * Given wire field data and the current {@link StructType}:
 * <ul>
 *   <li>Dropped fields → returns the type-default value (ignores wire and DEFAULT)</li>
 *   <li>Wire fields present in schema (active) → kept as-is</li>
 *   <li>Schema fields missing from wire (added after record was written) → filled from defaults or null</li>
 *   <li>Wire fields not in current schema → preserved for backward compatibility</li>
 * </ul>
 */
public final class SchemaResolver {

    private SchemaResolver() {}

    // ========================================================================
    // Write resolution
    // ========================================================================

    /**
     * Result of write resolution.
     *
     * @param resolved  The resolved StructValue ready for serialization, or null if errors
     * @param error     Error message, or null if resolution succeeded
     */
    public record WriteResult(StructValue resolved, String error) {
        public boolean hasError() { return error != null; }
    }

    /**
     * Resolves a client-provided value against the current server schema for writing.
     *
     * @param clientValue  The value the client wants to write
     * @param serverType   The current (evolved) struct type
     * @return Resolution result with either a resolved value or an error
     */
    public static WriteResult resolveWrite(StructValue clientValue, StructType serverType) {
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
        Map<String, Object> clientFields = clientValue.fields();

        // Check for writes to dropped fields
        for (String clientField : clientFields.keySet()) {
            StructTypeField schemaField = serverType.fields().get(clientField);
            if (schemaField != null && schemaField.dropped()) {
                return new WriteResult(null,
                    "Cannot write to dropped field '" + clientField + "'");
            }
        }

        // Build resolved value: iterate server schema fields in declaration order
        for (var entry : serverType.fields().entrySet()) {
            String fieldName = entry.getKey();
            StructTypeField field = entry.getValue();

            if (field.dropped()) {
                // Dropped fields are not written
                continue;
            }

            if (clientFields.containsKey(fieldName)) {
                resolved.put(fieldName, clientFields.get(fieldName));
            }
            // Omitted fields are NOT filled at write time — defaults and
            // nullable resolution happens at read time via resolveRead.
        }

        return new WriteResult(
            new StructValue(serverType, resolved),
            null
        );
    }

    // ========================================================================
    // Read resolution
    // ========================================================================

    /**
     * Resolves wire data against the current server schema for reading.
     *
     * @param wireFields  The raw field-name → value map from the wire
     * @param serverType  The current (evolved) struct type
     * @return A resolved StructValue with all fields reconciled
     */
    public static StructValue resolveRead(Map<String, Object> wireFields, StructType serverType) {
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();

        // First, walk the server schema in declaration order
        for (var entry : serverType.fields().entrySet()) {
            String fieldName = entry.getKey();
            StructTypeField field = entry.getValue();

            if (field.dropped()) {
                // Dropped field: wire value and DEFAULT are both ignored.
                // Nullable fields → null; non-nullable → type default.
                resolved.put(fieldName,
                    field.nullable() ? null : typeDefault(field.type()));
            } else if (wireFields.containsKey(fieldName)) {
                // Wire has this field — use it
                resolved.put(fieldName, wireFields.get(fieldName));
            } else if (field.defaultValue().isPresent()) {
                // Field was added after this record was written — fill default
                resolved.put(fieldName, field.defaultValue().get());
            } else if (field.nullable()) {
                // Nullable field missing from wire — null
                resolved.put(fieldName, null);
            }
            // else: non-nullable field with no default, not on wire — omit
            // (this would be a required field from before the record was written,
            //  which shouldn't happen in practice)
        }

        // Then, include wire fields NOT in the current schema
        // (forward compatibility — data from a future schema version)
        for (var entry : wireFields.entrySet()) {
            if (!resolved.containsKey(entry.getKey())) {
                resolved.put(entry.getKey(), entry.getValue());
            }
        }

        return new StructValue(serverType, resolved);
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    /**
     * Returns the type-default (zero) value for the given type.
     * For primitives this delegates to {@link PrimitiveKind#defaultValue()};
     * for all other types returns {@code null}.
     */
    private static Object typeDefault(AnyType type) {
        if (type instanceof PrimitiveType pt) {
            return pt.kind().defaultValue();
        }
        return null;
    }
}
