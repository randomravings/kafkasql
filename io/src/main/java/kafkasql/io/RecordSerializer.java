package kafkasql.io;

/**
 * A lightweight interface for serializing record values into byte arrays.
 * <p>
 * This interface defines methods for serializing both the key and value
 * of a record. Implementations should provide type-safe serialization logic
 * for specific record value types.
 *
 * @param <T> The type of record values to serialize
 */
public interface RecordSerializer<T extends RecordValue<T>> {
    /**
     * Serializes the key of the given record value into a byte array.
     * @param value The record value to serialize
     * @return The serialized key as a byte array
     */
    byte[] serializeKey(T value);

    /**
     * Serializes the value of the given record value into a byte array.
     * @param value The record value to serialize
     * @return The serialized value as a byte array
     */
    byte[] serialize(T value);
}
