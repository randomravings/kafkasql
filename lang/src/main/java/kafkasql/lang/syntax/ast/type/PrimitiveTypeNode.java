package kafkasql.lang.syntax.ast.type;

import kafkasql.lang.diagnostics.Range;
import kafkasql.runtime.type.PrimitiveKind;

public final class PrimitiveTypeNode implements TypeNode {
    private final Range _range;
    private final PrimitiveKind _kind;
    private final Long _length;
    private final Long _precision;
    private final Long _scale;

    private PrimitiveTypeNode(Range range, PrimitiveKind type) {
        _range = range;
        _kind = type;
        _length = null;
        _precision = null;
        _scale = null;
    }

    private PrimitiveTypeNode(Range range, PrimitiveKind type, Long length) {
        _range = range;
        _kind = type;
        _length = length;
        _precision = null;
        _scale = null;
    }

    private PrimitiveTypeNode(Range range, PrimitiveKind type, Long precision, Long scale) {
        _range = range;
        _kind = type;
        _length = null;
        _precision = precision;
        _scale = scale;
    }

    public Range range() {
        return _range;
    }

    public PrimitiveKind kind() {
        return _kind;
    }

    public boolean hasLength() {
        return _length != null;
    }

    public boolean hasPrecision() {
        return _precision != null;
    }

    public boolean hasScale() {
        return _scale != null;
    }

    public Long length() {
        return _length;
    }

    public Long precision() {
        return _precision;
    }

    public Long scale() {
        return _scale;
    }

    public static PrimitiveTypeNode createBool(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.BOOLEAN);
    }
    public static PrimitiveTypeNode bool(Range range) {
        return createBool(range);
    }
    public static PrimitiveTypeNode int8(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.INT8);
    }
    public static PrimitiveTypeNode int16(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.INT16);
    }
    public static PrimitiveTypeNode int32(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.INT32);
    }
    public static PrimitiveTypeNode int64(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.INT64);
    }
    public static PrimitiveTypeNode float32(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.FLOAT32);
    }
    public static PrimitiveTypeNode float64(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.FLOAT64);
    }
    public static PrimitiveTypeNode decimal(Range range, long precision, long scale) {
        return new PrimitiveTypeNode(range, PrimitiveKind.DECIMAL, precision, scale);
    }
    public static PrimitiveTypeNode string(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.STRING);
    }
    public static PrimitiveTypeNode string(Range range, long length) {
        return new PrimitiveTypeNode(range, PrimitiveKind.STRING, length);
    }
    public static PrimitiveTypeNode bytes(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.BYTES);
    }
    public static PrimitiveTypeNode bytes(Range range, long length) {
        return new PrimitiveTypeNode(range, PrimitiveKind.BYTES, length);
    }
    public static PrimitiveTypeNode uuid(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.UUID);
    }
    public static PrimitiveTypeNode date(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.DATE);
    }
    public static PrimitiveTypeNode time(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.TIME, 0L, null);
    }
    public static PrimitiveTypeNode time(Range range, long precision) {
        return new PrimitiveTypeNode(range, PrimitiveKind.TIME, precision, null);
    }
    public static PrimitiveTypeNode timestamp(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.TIMESTAMP, 0L, null);
    }
    public static PrimitiveTypeNode timestamp(Range range, long precision) {
        return new PrimitiveTypeNode(range, PrimitiveKind.TIMESTAMP, precision, null);
    }
    public static PrimitiveTypeNode timestampTz(Range range, long precision) {
        return new PrimitiveTypeNode(range, PrimitiveKind.TIMESTAMP_TZ, precision, null);
    }
    public static PrimitiveTypeNode timestampTz(Range range) {
        return new PrimitiveTypeNode(range, PrimitiveKind.TIMESTAMP_TZ, 0L, null);
    }
}
