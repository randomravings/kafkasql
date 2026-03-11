package kafkasql.io;

import kafkasql.io.codec.Decoder;
import kafkasql.io.codec.Encoder;
import kafkasql.runtime.value.RecordValue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Generic serializer for generated KafkaSQL types using binary encoding.
 * 
 * Uses reflection to introspect record components and serialize/deserialize
 * fields according to their types. This is the "intelligent" layer that knows
 * how to handle the "dumb" generated data types.
 * 
 * Design Philosophy (Avro-style):
 * - Generated types are simple records (dumb data containers)
 * - Schema information drives serialization (smart serializers)
 * - Binary format for efficiency
 * - No self-describing data (schema required for deserialization)
 */
public class BinarySerializer {
    
    /**
     * Serializes a record to binary format.
     * 
     * @param record The record instance to serialize
     * @param out The output stream to write to
     * @param <T> The type of record (must implement RecordValue)
     * @throws Exception If serialization fails
     */
    public static <T extends RecordValue<T>> void serialize(T record, OutputStream out) throws Exception {
        if (record == null) {
            return;
        }
        
        Class<?> clazz = record.getClass();
        
        // Handle enums
        if (clazz.isEnum()) {
            Enum<?> enumValue = (Enum<?>) record;
            // Assume enums have getValue() method
            try {
                int value = (int) clazz.getMethod("getValue").invoke(record);
                Encoder.writeInt32(out, value);
            } catch (NoSuchMethodException e) {
                // Fallback to ordinal
                Encoder.writeInt32(out, enumValue.ordinal());
            }
            return;
        }
        
        // Handle records
        if (clazz.isRecord()) {
            RecordComponent[] components = clazz.getRecordComponents();
            for (RecordComponent component : components) {
                Object value = component.getAccessor().invoke(record);
                serializeField(value, component.getType(), out);
            }
            return;
        }
        
        throw new IllegalArgumentException("Unsupported type: " + clazz.getName());
    }
    
    /**
     * Serializes a single field value.
     */
    private static void serializeField(Object value, Class<?> type, OutputStream out) throws Exception {
        if (value == null) {
            // For nullable fields, write a null marker
            Encoder.writeBool(out, false);
            return;
        }
        
        // Non-null marker
        if (!type.isPrimitive()) {
            Encoder.writeBool(out, true);
        }
        
        // Primitives
        if (type == boolean.class || type == Boolean.class) {
            Encoder.writeBool(out, (Boolean) value);
        } else if (type == byte.class || type == Byte.class) {
            Encoder.writeInt8(out, (Byte) value);
        } else if (type == short.class || type == Short.class) {
            Encoder.writeInt16(out, (Short) value);
        } else if (type == int.class || type == Integer.class) {
            Encoder.writeInt32(out, (Integer) value);
        } else if (type == long.class || type == Long.class) {
            Encoder.writeInt64(out, (Long) value);
        } else if (type == float.class || type == Float.class) {
            Encoder.writeFloat32(out, (Float) value);
        } else if (type == double.class || type == Double.class) {
            Encoder.writeFloat64(out, (Double) value);
        } else if (type == String.class) {
            Encoder.writeString(out, (String) value);
        } else if (type == byte[].class) {
            Encoder.writeBytes(out, (byte[]) value);
        } else if (type == UUID.class) {
            Encoder.writeUUID(out, (UUID) value);
        } else if (type == BigDecimal.class) {
            Encoder.writeDecimal(out, (BigDecimal) value);
        } else if (type == LocalDate.class) {
            // Convert LocalDate to epoch day
            Encoder.writeInt64(out, ((LocalDate) value).toEpochDay());
        } else if (type == LocalTime.class) {
            // Encode LocalTime as nano-of-day
            Encoder.writeInt64(out, ((LocalTime) value).toNanoOfDay());
        } else if (type == LocalDateTime.class) {
            // Encode LocalDateTime as epoch seconds + nanos
            LocalDateTime ldt = (LocalDateTime) value;
            Encoder.writeInt64(out, ldt.toEpochSecond(ZoneOffset.UTC));
            Encoder.writeInt32(out, ldt.getNano());
        } else if (type == ZonedDateTime.class) {
            // Encode ZonedDateTime as epoch seconds + nanos + zone ID
            ZonedDateTime zdt = (ZonedDateTime) value;
            Encoder.writeInt64(out, zdt.toEpochSecond());
            Encoder.writeInt32(out, zdt.getNano());
            Encoder.writeString(out, zdt.getZone().getId());
        } else if (type.isEnum()) {
            // Nested enum - cast to RecordValue (all generated enums implement it)
            @SuppressWarnings("unchecked")
            RecordValue<?> nested = (RecordValue<?>) value;
            serialize((RecordValue) nested, out);
        } else if (type.isRecord()) {
            // Nested record - cast to RecordValue (all generated records implement it)
            @SuppressWarnings("unchecked")
            RecordValue<?> nested = (RecordValue<?>) value;
            serialize((RecordValue) nested, out);
        } else if (List.class.isAssignableFrom(type)) {
            List<?> list = (List<?>) value;
            Encoder.writeVarInt32(out, list.size());
            for (Object item : list) {
                serializeField(item, item.getClass(), out);
            }
        } else if (Map.class.isAssignableFrom(type)) {
            Map<?, ?> map = (Map<?, ?>) value;
            Encoder.writeVarInt32(out, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                serializeField(entry.getKey(), entry.getKey().getClass(), out);
                serializeField(entry.getValue(), entry.getValue().getClass(), out);
            }
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + type.getName());
        }
    }
    
    /**
     * Serializes a record to a byte array.
     * 
     * @param record The record instance to serialize
     * @param <T> The type of record (must implement RecordValue)
     * @return The serialized byte array
     * @throws Exception If serialization fails
     */
    public static <T extends RecordValue<T>> byte[] toByteArray(T record) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialize(record, baos);
        return baos.toByteArray();
    }
    
    /**
     * Deserializes a record from binary format.
     * 
     * @param clazz The class of the record to deserialize (must implement RecordValue)
     * @param in The input stream to read from
     * @param <T> The type of record (must implement RecordValue)
     * @return The deserialized record instance
     * @throws Exception If deserialization fails
     */
    public static <T extends RecordValue<T>> T deserialize(Class<T> clazz, InputStream in) throws Exception {
        // Handle enums
        if (clazz.isEnum()) {
            int value = Decoder.decodeInt32(in);
            // Try to find enum by value
            try {
                T[] constants = clazz.getEnumConstants();
                for (T constant : constants) {
                    int enumValue = (int) clazz.getMethod("getValue").invoke(constant);
                    if (enumValue == value) {
                        return constant;
                    }
                }
                throw new IllegalArgumentException("Unknown enum value: " + value);
            } catch (NoSuchMethodException e) {
                // Fallback to ordinal
                return clazz.getEnumConstants()[value];
            }
        }
        
        // Handle sealed interfaces (e.g., compiled stream types with a single variant)
        if (clazz.isSealed()) {
            Class<?>[] permitted = clazz.getPermittedSubclasses();
            if (permitted.length == 1) {
                @SuppressWarnings("unchecked")
                Class<T> subclass = (Class<T>) permitted[0];
                return deserialize(subclass, in);
            }
            throw new IllegalArgumentException(
                "Sealed interface " + clazz.getName() + " has " + permitted.length +
                " permitted subclasses; only single-variant sealed interfaces are supported"
            );
        }
        
        // Handle records
        if (clazz.isRecord()) {
            RecordComponent[] components = clazz.getRecordComponents();
            Object[] args = new Object[components.length];
            
            for (int i = 0; i < components.length; i++) {
                args[i] = deserializeField(components[i].getType(), in);
            }
            
            // Find canonical constructor
            Class<?>[] paramTypes = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                paramTypes[i] = components[i].getType();
            }
            
            return clazz.getDeclaredConstructor(paramTypes).newInstance(args);
        }
        
        throw new IllegalArgumentException("Unsupported type: " + clazz.getName());
    }
    
    /**
     * Deserializes a single field value.
     */
    private static Object deserializeField(Class<?> type, InputStream in) throws Exception {
        // Check null marker for non-primitives
        if (!type.isPrimitive()) {
            boolean isPresent = Decoder.decodeBoolean(in);
            if (!isPresent) {
                return null;
            }
        }
        
        // Primitives
        if (type == boolean.class || type == Boolean.class) {
            return Decoder.decodeBoolean(in);
        } else if (type == byte.class || type == Byte.class) {
            return Decoder.decodeInt8(in);
        } else if (type == short.class || type == Short.class) {
            return Decoder.decodeInt16(in);
        } else if (type == int.class || type == Integer.class) {
            return Decoder.decodeInt32(in);
        } else if (type == long.class || type == Long.class) {
            return Decoder.decodeInt64(in);
        } else if (type == float.class || type == Float.class) {
            return Decoder.decodeFloat32(in);
        } else if (type == double.class || type == Double.class) {
            return Decoder.decodeFloat64(in);
        } else if (type == String.class) {
            return Decoder.decodeString(in);
        } else if (type == byte[].class) {
            return Decoder.decodeBytes(in);
        } else if (type == UUID.class) {
            return Decoder.decodeUUID(in);
        } else if (type == BigDecimal.class) {
            // Decode BigDecimal from unscaled bytes
            byte[] unscaled = Decoder.decodeBytes(in);
            return new BigDecimal(new java.math.BigInteger(unscaled));
        } else if (type == LocalDate.class) {
            // Decode LocalDate from epoch day
            long epochDay = Decoder.decodeInt64(in);
            return LocalDate.ofEpochDay(epochDay);
        } else if (type == LocalTime.class) {
            // Decode LocalTime from nano-of-day
            long nanoOfDay = Decoder.decodeInt64(in);
            return LocalTime.ofNanoOfDay(nanoOfDay);
        } else if (type == LocalDateTime.class) {
            // Decode LocalDateTime from epoch seconds + nanos
            long epochSecond = Decoder.decodeInt64(in);
            int nanos = Decoder.decodeInt32(in);
            return LocalDateTime.ofEpochSecond(epochSecond, nanos, ZoneOffset.UTC);
        } else if (type == ZonedDateTime.class) {
            // Decode ZonedDateTime from epoch seconds + nanos + zone ID
            long epochSecond = Decoder.decodeInt64(in);
            int nanos = Decoder.decodeInt32(in);
            String zoneId = Decoder.decodeString(in);
            return ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(epochSecond, nanos),
                ZoneId.of(zoneId)
            );
        } else if (type.isEnum()) {
            // Nested enum - cast class to RecordValue (all generated enums implement it)
            @SuppressWarnings("unchecked")
            Class<? extends RecordValue> recordClass = (Class<? extends RecordValue>) type;
            return deserialize(recordClass, in);
        } else if (type.isRecord()) {
            // Nested record - cast class to RecordValue (all generated records implement it)
            @SuppressWarnings("unchecked")
            Class<? extends RecordValue> recordClass = (Class<? extends RecordValue>) type;
            return deserialize(recordClass, in);
        } else if (List.class.isAssignableFrom(type)) {
            int size = Decoder.decodeVarInt32(in);
            List<Object> list = new ArrayList<>(size);
            // Note: We'd need type parameters to properly deserialize - this is a limitation
            // For now, this is a placeholder
            throw new UnsupportedOperationException("List deserialization requires generic type info");
        } else if (Map.class.isAssignableFrom(type)) {
            throw new UnsupportedOperationException("Map deserialization requires generic type info");
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + type.getName());
        }
    }
}
