package kafkasql.runtime.type;

import java.math.BigDecimal;

public enum PrimitiveKind {
    BOOLEAN,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
    DECIMAL,
    STRING,
    BYTES,
    UUID,
    DATE,
    TIME,
    TIMESTAMP,
    TIMESTAMP_TZ;

    /**
     * Returns the type-default (zero) value for this primitive kind.
     * Used when a dropped field must still appear in read projections.
     */
    public Object defaultValue() {
        return switch (this) {
            case BOOLEAN      -> false;
            case INT8         -> (byte) 0;
            case INT16        -> (short) 0;
            case INT32        -> 0;
            case INT64        -> 0L;
            case FLOAT32      -> 0.0f;
            case FLOAT64      -> 0.0;
            case DECIMAL      -> BigDecimal.ZERO;
            case STRING       -> "";
            case BYTES        -> new byte[0];
            case UUID         -> new java.util.UUID(0L, 0L);
            case DATE         -> java.time.LocalDate.EPOCH;
            case TIME         -> java.time.LocalTime.MIDNIGHT;
            case TIMESTAMP    -> java.time.LocalDateTime.of(java.time.LocalDate.EPOCH, java.time.LocalTime.MIDNIGHT);
            case TIMESTAMP_TZ -> java.time.OffsetDateTime.of(java.time.LocalDate.EPOCH, java.time.LocalTime.MIDNIGHT, java.time.ZoneOffset.UTC);
        };
    }
}
