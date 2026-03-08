package kafkasql.runtime.value;

/**
 * Marker interface for compiled/generated record types from KafkaSQL schemas.
 * <p>
 * This interface is for type-safe, compile-time generated records (enums, structs, streams).
 * It uses self-referential generics to enable type-safe operations and sealed hierarchies.
 * <p>
 * Contrast with {@code kafkasql.runtime.value.Value} which represents dynamic/runtime values
 * that are more flexible but not type-safe.
 * <p>
 * Design:
 * - Streams are sealed interfaces: {@code sealed interface MyStream extends RecordValue<MyStream>}
 * - Members implement the stream: {@code record Member(...) implements MyStream}
 * - This closes the type loop for exhaustive pattern matching and type safety
 *
 * @param <T> The type of the record value (self-referential)
 */
public interface RecordValue<T extends RecordValue<T>> { }
