package kafkasql.runtime.value;

import java.util.LinkedHashMap;
import java.util.Objects;

import kafkasql.runtime.type.StructType;

/**
 * Runtime value instance of a STRUCT type.
 *
 * Values are canonical JVM values:
 *  - primitives / wrappers
 *  - BigDecimal for DECIMAL
 *  - byte[] for BYTES/FIXED
 *  - EnumValue, UnionValue, StructValue
 *  - ListValue, MapValue
 *  - null for nullable fields
 */
public final class StructValue implements Value {

    private final StructType type;
    private final LinkedHashMap<String, Object> fields;

    public StructValue(StructType type, LinkedHashMap<String, Object> fields) {
        this.type = Objects.requireNonNull(type, "type");
        this.fields = Objects.requireNonNull(fields, "fields");
    }

    public StructType type() {
        return type;
    }

    public LinkedHashMap<String, Object> fields() {
        return fields;
    }

    public Object get(String fieldName) {
        return fields.get(fieldName);
    }

    @Override
    public String toString() {
        return "StructValue(" + type.fqn().toString() + " " + fields + ")";
    }
}
