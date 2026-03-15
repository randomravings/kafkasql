package kafkasql.runtime.value;

import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

import kafkasql.runtime.type.ScalarType;

/**
 * Runtime value of a SCALAR (named primitive wrapper) type.
 */
public final class ScalarValue implements Value {

    private final ScalarType type;
    private final Object value;

    public ScalarValue(ScalarType type, Object value) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = Objects.requireNonNull(value, "value");
        validatePrimitive(type, value);
    }

    @Override
    public ScalarType type() {
        return type;
    }

    public Object value() {
        return value;
    }

    public boolean booleanValue() { return (Boolean) value; }
    public byte int8Value() { return (Byte) value; }
    public short int16Value() { return (Short) value; }
    public int int32Value() { return (Integer) value; }
    public long int64Value() { return (Long) value; }
    public float float32Value() { return (Float) value; }
    public double float64Value() { return (Double) value; }
    public String stringValue() { return (String) value; }
    public byte[] bytesValue() { return (byte[]) value; }
    public UUID uuidValue() { return (UUID) value; }
    public BigDecimal decimalValue() { return (BigDecimal) value; }
    public LocalDate dateValue() { return (LocalDate) value; }
    public LocalTime timeValue() { return (LocalTime) value; }
    public LocalDateTime timestampValue() { return (LocalDateTime) value; }
    public ZonedDateTime timestampTzValue() { return (ZonedDateTime) value; }

    private static void validatePrimitive(ScalarType type, Object value) {
        Class<?> expected = switch (type.primitive().kind()) {
            case BOOLEAN -> Boolean.class;
            case INT8 -> Byte.class;
            case INT16 -> Short.class;
            case INT32 -> Integer.class;
            case INT64 -> Long.class;
            case FLOAT32 -> Float.class;
            case FLOAT64 -> Double.class;
            case STRING -> String.class;
            case BYTES -> byte[].class;
            case UUID -> UUID.class;
            case DECIMAL -> BigDecimal.class;
            case DATE -> LocalDate.class;
            case TIME -> LocalTime.class;
            case TIMESTAMP -> LocalDateTime.class;
            case TIMESTAMP_TZ -> ZonedDateTime.class;
        };
        if (!expected.isInstance(value)) {
            throw new IllegalArgumentException(
                "Expected " + expected.getSimpleName() + " for " + type.fqn() +
                " but got " + value.getClass().getSimpleName()
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScalarValue that)) return false;
        return type.equals(that.type) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return type.fqn().name() + "(" + value + ")";
    }
}
