package kafkasql.runtime.stream;

/**
 * Marker interface for KafkaSQL streams.
 * 
 * <p>In this framework, the stream is the primary type - everything is built around
 * reading from and writing to streams. A stream represents a sequence of messages
 * that can be of one or more related types.
 * 
 * <p>This is a marker interface only. Read and write operations are completely
 * independent and provided by separate interfaces:
 * <ul>
 *   <li><b>StreamReader&lt;T&gt;</b> - For reading messages from a stream</li>
 *   <li><b>StreamWriter&lt;T&gt;</b> - For writing messages to a stream</li>
 * </ul>
 * 
 * <p>Implementations may choose to implement either one or both interfaces depending
 * on their use case (read-only, write-only, or read-write).
 * 
 * @param <T> The base type for messages in this stream (either a CompiledStream type
 *            for sealed hierarchies, or StructValue for dynamic streams)
 */
public interface Stream<T> {
    // Marker interface - no methods
    // Provides common type bound for both readers and writers
}
