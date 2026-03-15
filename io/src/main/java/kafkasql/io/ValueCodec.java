package kafkasql.io;

import kafkasql.io.codec.Decoder;
import kafkasql.io.codec.Encoder;
import kafkasql.runtime.type.*;
import kafkasql.runtime.value.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Codec for serializing/deserializing dynamic {@link Value} types to binary format.
 * <p>
 * Handles the dynamic value hierarchy ({@link ScalarValue}, {@link EnumValue},
 * {@link StructValue}, {@link UnionValue}) using the embedded type metadata to drive serialization.
 * The binary format mirrors the generated writeTo/readFrom methods for primitive fields,
 * enabling compatibility between compiled and dynamic representations.
 * <p>
 * Design:
 * <ul>
 *   <li>Schema-driven: type metadata determines wire format (no self-describing tags)</li>
 *   <li>Nullable fields: boolean presence marker followed by value</li>
 *   <li>Enums: encoded as int32 numeric value</li>
 *   <li>Unions: varint member index + member value</li>
 *   <li>Structs: fields serialized in declaration order</li>
 * </ul>
 */
public class ValueCodec {

    /**
     * Encodes a {@link Value} to the given output stream.
     */
    public static void encode(Value value, OutputStream out) throws Exception {
        switch (value) {
            case ScalarValue sv -> encodePrimitive(sv.value(), sv.type().primitive(), out);
            case EnumValue ev -> Encoder.writeInt32(out, (int) ev.numericValue());
            case StructValue sv -> encodeStruct(sv, out);
            case UnionValue uv -> encodeUnion(uv, out);
        }
    }

    /**
     * Serializes a {@link Value} to a byte array.
     */
    public static byte[] toByteArray(Value value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        encode(value, baos);
        return baos.toByteArray();
    }

    /**
     * Decodes a {@link Value} from the given input stream using the provided type.
     *
     * @param type The expected type (must be a ComplexType: StructType, EnumType, or UnionType)
     * @param in   The input stream to read from
     * @return The decoded Value instance
     */
    public static Value decode(AnyType type, InputStream in) throws Exception {
        return switch (type) {
            case EnumType et -> decodeEnum(et, in);
            case StructType st -> decodeStruct(st, in);
            case UnionType ut -> decodeUnion(ut, in);
            case ScalarType sc -> new ScalarValue(sc, decodePrimitive(sc.primitive(), in));
            default -> throw new IllegalArgumentException("Cannot decode Value for type: " + type);
        };
    }

    /**
     * Deserializes a {@link Value} from a byte array.
     */
    public static Value fromByteArray(AnyType type, byte[] data) throws Exception {
        return decode(type, new ByteArrayInputStream(data));
    }

    // ========================================================================
    // Struct
    // ========================================================================

    private static void encodeStruct(StructValue sv, OutputStream out) throws Exception {
        for (var entry : sv.type().fields().entrySet()) {
            StructTypeField field = entry.getValue();
            Object fieldValue = sv.get(entry.getKey());
            encodeField(fieldValue, field.type(), field.nullable(), out);
        }
    }

    private static StructValue decodeStruct(StructType type, InputStream in) throws Exception {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        for (var entry : type.fields().entrySet()) {
            StructTypeField field = entry.getValue();
            Object value = decodeField(field.type(), field.nullable(), in);
            fields.put(entry.getKey(), value);
        }
        return new StructValue(type, fields);
    }

    // ========================================================================
    // Enum
    // ========================================================================

    private static EnumValue decodeEnum(EnumType type, InputStream in) throws Exception {
        int numericValue = Decoder.decodeInt32(in);
        for (EnumTypeSymbol symbol : type.symbols()) {
            if (symbol.value() == numericValue) {
                return new EnumValue(type, symbol);
            }
        }
        throw new IllegalArgumentException(
            "Unknown enum value " + numericValue + " for type " + type.fqn()
        );
    }

    // ========================================================================
    // Union
    // ========================================================================

    private static void encodeUnion(UnionValue uv, OutputStream out) throws Exception {
        // Write member index
        int idx = 0;
        for (String name : uv.type().members().keySet()) {
            if (name.equals(uv.memberName())) break;
            idx++;
        }
        Encoder.writeVarInt32(out, idx);

        // Write member value
        UnionTypeMember member = uv.type().members().get(uv.memberName());
        encodeFieldValue(uv.value(), member.typ(), out);
    }

    private static UnionValue decodeUnion(UnionType type, InputStream in) throws Exception {
        int idx = Decoder.decodeVarInt32(in);

        // Find member by index
        int i = 0;
        for (var entry : type.members().entrySet()) {
            if (i == idx) {
                UnionTypeMember member = entry.getValue();
                Object value = decodeFieldValue(member.typ(), in);
                return new UnionValue(type, entry.getKey(), value);
            }
            i++;
        }
        throw new IllegalArgumentException(
            "Unknown union member index " + idx + " for type " + type.fqn()
        );
    }

    // ========================================================================
    // Field encoding (handles nullability)
    // ========================================================================

    private static void encodeField(Object value, AnyType type, boolean nullable, OutputStream out) throws Exception {
        if (nullable) {
            if (value == null) {
                Encoder.writeBool(out, false);
                return;
            }
            Encoder.writeBool(out, true);
        }
        encodeFieldValue(value, type, out);
    }

    private static Object decodeField(AnyType type, boolean nullable, InputStream in) throws Exception {
        if (nullable) {
            boolean present = Decoder.decodeBoolean(in);
            if (!present) return null;
        }
        return decodeFieldValue(type, in);
    }

    // ========================================================================
    // Field value encoding (no null handling — caller manages that)
    // ========================================================================

    private static void encodeFieldValue(Object value, AnyType type, OutputStream out) throws Exception {
        switch (type) {
            case PrimitiveType pt -> encodePrimitive(value, pt, out);
            case ScalarType st -> encodePrimitive(((ScalarValue) value).value(), st.primitive(), out);
            case EnumType ignored -> {
                EnumValue ev = (EnumValue) value;
                Encoder.writeInt32(out, (int) ev.numericValue());
            }
            case StructType ignored -> encodeStruct((StructValue) value, out);
            case UnionType ignored -> encodeUnion((UnionValue) value, out);
            case ListType lt -> {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                Encoder.writeVarInt32(out, list.size());
                for (Object item : list) {
                    encodeFieldValue(item, lt.item(), out);
                }
            }
            case MapType mt -> {
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) value;
                Encoder.writeVarInt32(out, map.size());
                for (var entry : map.entrySet()) {
                    encodeFieldValue(entry.getKey(), mt.key(), out);
                    encodeFieldValue(entry.getValue(), mt.value(), out);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported field type: " + type);
        }
    }

    private static Object decodeFieldValue(AnyType type, InputStream in) throws Exception {
        return switch (type) {
            case PrimitiveType pt -> decodePrimitive(pt, in);
            case ScalarType st -> new ScalarValue(st, decodePrimitive(st.primitive(), in));
            case EnumType et -> decodeEnum(et, in);
            case StructType st -> decodeStruct(st, in);
            case UnionType ut -> decodeUnion(ut, in);
            case ListType lt -> {
                int size = Decoder.decodeVarInt32(in);
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(decodeFieldValue(lt.item(), in));
                }
                yield list;
            }
            case MapType mt -> {
                int size = Decoder.decodeVarInt32(in);
                LinkedHashMap<Object, Object> map = new LinkedHashMap<>(size);
                for (int i = 0; i < size; i++) {
                    Object key = decodeFieldValue(mt.key(), in);
                    Object val = decodeFieldValue(mt.value(), in);
                    map.put(key, val);
                }
                yield map;
            }
            default -> throw new IllegalArgumentException("Unsupported field type: " + type);
        };
    }

    // ========================================================================
    // Primitive encoding
    // ========================================================================

    private static void encodePrimitive(Object value, PrimitiveType pt, OutputStream out) throws Exception {
        switch (pt.kind()) {
            case BOOLEAN -> Encoder.writeBool(out, (Boolean) value);
            case INT8 -> Encoder.writeInt8(out, (Byte) value);
            case INT16 -> Encoder.writeInt16(out, (Short) value);
            case INT32 -> Encoder.writeInt32(out, (Integer) value);
            case INT64 -> Encoder.writeInt64(out, (Long) value);
            case FLOAT32 -> Encoder.writeFloat32(out, (Float) value);
            case FLOAT64 -> Encoder.writeFloat64(out, (Double) value);
            case STRING -> Encoder.writeString(out, (String) value);
            case BYTES -> Encoder.writeBytes(out, (byte[]) value);
            case UUID -> Encoder.writeUUID(out, (java.util.UUID) value);
            case DECIMAL -> Encoder.writeDecimal(out, (BigDecimal) value);
            case DATE -> Encoder.writeInt64(out, ((LocalDate) value).toEpochDay());
            case TIME -> Encoder.writeInt64(out, ((LocalTime) value).toNanoOfDay());
            case TIMESTAMP -> {
                LocalDateTime ldt = (LocalDateTime) value;
                Encoder.writeInt64(out, ldt.toEpochSecond(ZoneOffset.UTC));
                Encoder.writeInt32(out, ldt.getNano());
            }
            case TIMESTAMP_TZ -> {
                ZonedDateTime zdt = (ZonedDateTime) value;
                Encoder.writeInt64(out, zdt.toEpochSecond());
                Encoder.writeInt32(out, zdt.getNano());
                Encoder.writeString(out, zdt.getZone().getId());
            }
        }
    }

    private static Object decodePrimitive(PrimitiveType pt, InputStream in) throws Exception {
        return switch (pt.kind()) {
            case BOOLEAN -> Decoder.decodeBoolean(in);
            case INT8 -> Decoder.decodeInt8(in);
            case INT16 -> Decoder.decodeInt16(in);
            case INT32 -> Decoder.decodeInt32(in);
            case INT64 -> Decoder.decodeInt64(in);
            case FLOAT32 -> Decoder.decodeFloat32(in);
            case FLOAT64 -> Decoder.decodeFloat64(in);
            case STRING -> Decoder.decodeString(in);
            case BYTES -> Decoder.decodeBytes(in);
            case UUID -> Decoder.decodeUUID(in);
            case DECIMAL -> {
                byte[] unscaled = Decoder.decodeBytes(in);
                yield new BigDecimal(new java.math.BigInteger(unscaled));
            }
            case DATE -> LocalDate.ofEpochDay(Decoder.decodeInt64(in));
            case TIME -> LocalTime.ofNanoOfDay(Decoder.decodeInt64(in));
            case TIMESTAMP -> {
                long epochSecond = Decoder.decodeInt64(in);
                int nanos = Decoder.decodeInt32(in);
                yield LocalDateTime.ofEpochSecond(epochSecond, nanos, ZoneOffset.UTC);
            }
            case TIMESTAMP_TZ -> {
                long epochSecond = Decoder.decodeInt64(in);
                int nanos = Decoder.decodeInt32(in);
                String zoneId = Decoder.decodeString(in);
                yield ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(epochSecond, nanos),
                    ZoneId.of(zoneId)
                );
            }
        };
    }
}
