package kafkasql.runtime.stream;

import kafkasql.runtime.value.RecordValue;

/**
 * Marker interface for compiled/generated stream message type hierarchies.
 * 
 * <p>CompiledStream represents the <b>message type hierarchy</b>, not the stream
 * implementation. It is a sealed interface that groups all message variants for
 * a particular stream schema.
 * 
 * <p>Compiled streams are generated from KafkaSQL schema definitions (.kafka files).
 * They are represented as sealed interfaces with static nested record types for
 * each message variant (inline structs or referenced types).
 * 
 * <p><b>Example generated code:</b>
 * <pre>{@code
 * public sealed interface SymbolEventLog 
 *     extends CompiledStream<SymbolEventLog> 
 *     permits SymbolEventLog.SymbolEvent {
 *     
 *     record SymbolEvent(
 *         UUID eventId,
 *         LocalDateTime timestamp,
 *         String data
 *     ) implements SymbolEventLog {}
 * }
 * }</pre>
 * 
 * <p>This approach provides:
 * <ul>
 *   <li>Type-safe pattern matching across all message types</li>
 *   <li>Sealed hierarchy enforcement at compile-time</li>
 *   <li>Efficient serialization via schema-driven codecs</li>
 *   <li>IDE support for exhaustiveness checking</li>
 * </ul>
 * 
 * <p>All message types in a compiled stream must implement RecordValue and the
 * stream interface itself, creating a closed type hierarchy.
 * 
 * <p><b>Generic read/write operations:</b> This interface provides default methods
 * for reading and writing messages in a stream-agnostic way. Implementations delegate
 * to the appropriate {@link Stream} implementation for the concrete stream type.
 * 
 * @param <T> The stream type itself (self-referential for sealed hierarchies)
 */
public interface CompiledStream<T extends CompiledStream<T>> extends RecordValue<T> {
    
    /**
     * Writes a message to this stream.
     * 
     * <p>This is a convenience method that delegates to the underlying {@link StreamWriter}
     * implementation for this stream type. It allows for stream-agnostic code that
     * can write to any compiled stream.
     * 
     * <p>Each generated stream type should provide its own implementation of this method
     * that delegates to the appropriate {@link StreamWriter} implementation.
     * 
     * @param message The message to write to the stream
     * @throws Exception If the write operation fails
     */
    static <T extends CompiledStream<T>> void write(T message) throws Exception {
        throw new UnsupportedOperationException(
            "Write operation not implemented. Provide a static write method in the generated stream type " +
            "or use a StreamWriter<T> implementation directly."
        );
    }
    
    /**
     * Reads the next message from this stream type.
     * 
     * <p>This is a convenience method that delegates to the underlying {@link StreamReader}
     * implementation for this stream type. It allows for stream-agnostic code that
     * can read from any compiled stream.
     * 
     * <p>Each generated stream type should provide its own implementation of this method
     * that delegates to the appropriate {@link StreamReader} implementation.
     * 
     * @return The next message from the stream, or null if end of stream
     * @throws Exception If the read operation fails
     */
    static <T extends CompiledStream<T>> T read() throws Exception {
        throw new UnsupportedOperationException(
            "Read operation not implemented. Provide a static read method in the generated stream type " +
            "or use a StreamReader<T> implementation directly."
        );
    }
}
