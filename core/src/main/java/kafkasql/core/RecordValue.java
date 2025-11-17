package kafkasql.core;

/**
 * A lightweight interface representing a record value.
 * <p>
 * This interface serves as a marker for record value types used in
 * serialization and deserialization processes within KafkaSQL.
 *
 * @param <T> The type of the record value
 */
public interface RecordValue<T extends RecordValue<T>> { }
