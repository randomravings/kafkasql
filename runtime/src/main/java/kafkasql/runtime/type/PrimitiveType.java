package kafkasql.runtime.type;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PrimitiveType implements AnyType {
    private static final PrimitiveType BOOL_T =
        new PrimitiveType(PrimitiveKind.BOOLEAN, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType INT8_T =
        new PrimitiveType(PrimitiveKind.INT8, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType INT16_T =
        new PrimitiveType(PrimitiveKind.INT16, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType INT32_T =
        new PrimitiveType(PrimitiveKind.INT32, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType INT64_T =
        new PrimitiveType(PrimitiveKind.INT64, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType FLOAT32_T =
        new PrimitiveType(PrimitiveKind.FLOAT32, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType FLOAT64_T =
        new PrimitiveType(PrimitiveKind.FLOAT64, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType UUID_T =
        new PrimitiveType(PrimitiveKind.UUID, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType DATE_T =
        new PrimitiveType(PrimitiveKind.DATE, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType VSTRING_T =
        new PrimitiveType(PrimitiveKind.STRING, -1, (byte)-1, (byte)-1);
    private static final PrimitiveType VBYTES_T =
        new PrimitiveType(PrimitiveKind.BYTES, -1, (byte)-1, (byte)-1);

    private static final Map<Long, PrimitiveType> PARAMETERIZED_TYPES =
        new ConcurrentHashMap<>();

    private final PrimitiveKind _kind;
    private final int _length;
    private final byte _precision;
    private final byte _scale;

    private PrimitiveType(
        PrimitiveKind kind,
        int length,
        byte precision,
        byte scale
    ) {
        _kind = kind;
        _length = length;
        _precision = precision;
        _scale = scale;
    }

    public PrimitiveKind kind() {
        return _kind;
    }

    public int length() {
        return _length;
    }

    public byte precision() {
        return _precision;
    }

    public byte scale() {
        return _scale;
    }

    public boolean hasLength() {
        return _length != -1;
    }

    public boolean hasPrecision() {
        return _precision != -1;
    }

    public boolean hasScale() {
        return _scale != -1;
    }

    public boolean isBooleanKind() {
        return _kind == PrimitiveKind.BOOLEAN;
    }

    public boolean isIntegerKind() {
        return _kind == PrimitiveKind.INT8 ||
                _kind == PrimitiveKind.INT16 ||
                _kind == PrimitiveKind.INT32 ||
                _kind == PrimitiveKind.INT64;
    }

    public boolean isStringType() {
        return _kind == PrimitiveKind.STRING ||
                _kind == PrimitiveKind.BYTES ||
                _kind == PrimitiveKind.UUID;
    }

    public boolean isNumericKind() {
        return isIntegerKind() ||
                _kind == PrimitiveKind.FLOAT32 ||
                _kind == PrimitiveKind.FLOAT64 ||
                _kind == PrimitiveKind.DECIMAL;
    }

    public boolean isTemporalKind() {
        return _kind == PrimitiveKind.DATE ||
                _kind == PrimitiveKind.TIME ||
                _kind == PrimitiveKind.TIMESTAMP ||
                _kind == PrimitiveKind.TIMESTAMP_TZ;
    }

    public static PrimitiveType bool() {
        return BOOL_T;
    }
    public static PrimitiveType int8() {
        return INT8_T;
    }
    public static PrimitiveType int16() {
        return INT16_T;
    }
    public static PrimitiveType int32() {
        return INT32_T;
    }
    public static PrimitiveType int64() {
        return INT64_T;
    }
    public static PrimitiveType float32() {
        return FLOAT32_T;
    }
    public static PrimitiveType float64() {
        return FLOAT64_T;
    }
    public static PrimitiveType uuid() {
        return UUID_T;
    }
    public static PrimitiveType date() {
        return DATE_T;
    }
    public static PrimitiveType string() {
        return VSTRING_T;
    }
    public static PrimitiveType string(int length) {
        return get(PrimitiveKind.STRING, length);
    }
    public static PrimitiveType bytes() {
        return VBYTES_T;
    }
    public static PrimitiveType bytes(int length) {
        return get(PrimitiveKind.BYTES, length);
    }
    public static PrimitiveType time(byte precision) {
        return get(PrimitiveKind.TIME, precision);
    }
    public static PrimitiveType timestamp(byte precision) {
        return get(PrimitiveKind.TIMESTAMP, precision);
    }
    public static PrimitiveType timestampTz(byte precision) {
        return get(PrimitiveKind.TIMESTAMP_TZ, precision);
    }
    public static PrimitiveType decimal(byte precision, byte scale) {
        return get(PrimitiveKind.DECIMAL, precision, scale);
    }

    private static PrimitiveType get(
        PrimitiveKind kind,
        int length
    ) {
        byte precision = -1;
        byte scale = (byte)-1;
        return get(kind, length, precision, scale);
    }

    private static PrimitiveType get(
        PrimitiveKind kind,
        byte precision
    ) {
        int length = -1;
        byte scale = (byte)-1;
        return get(kind, length, precision, scale);
    }

    private static PrimitiveType get(
        PrimitiveKind kind,
        byte precision,
        byte scale
    ) {
        int length = -1;
        return get(kind, length, precision, scale);
    }

    private static PrimitiveType get(
        PrimitiveKind kind,
        int length,
        byte precision,
        byte scale
    ) {
        long key = key(kind, length, precision, scale);
        return PARAMETERIZED_TYPES.computeIfAbsent(
            key,
            unused -> new PrimitiveType(kind, length, precision, scale)
        );
    }

    private static long key(
        PrimitiveKind kind,
        int length,
        byte precision,
        byte scale
    ) {
        long key = (long)(kind.ordinal() & 0xFF) << 56;
        key |= ((long)length & 0xFFFFFFFFL) << 24;
        key |= ((long)precision & 0xFFL) << 16;
        key |= ((long)scale & 0xFFL) << 8;
        return key;
    }
}
