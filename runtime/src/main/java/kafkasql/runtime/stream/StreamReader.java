package kafkasql.runtime.stream;

/**
 * Interface for reading messages from a stream.
 * 
 * <p>Completely independent from {@link StreamWriter} - implementations can be
 * read-only and don't need write capabilities. Provides sequential access to
 * messages in a stream.
 * 
 * <p>Implementations may support different read patterns:
 * <ul>
 *   <li>Polling from message queues (Kafka, etc.)</li>
 *   <li>Sequential file reading</li>
 *   <li>Query result iteration</li>
 *   <li>In-memory stream replay</li>
 * </ul>
 * 
 * <p>Read operations are typically stateful - the reader maintains a position
 * or cursor in the stream and advances on each read.
 * 
 * @param <T> The type of messages read from this stream
 */
public interface StreamReader<T> {
    
    /**
     * Returns the name of this stream.
     * 
     * @return The stream name
     */
    String streamName();
    
    /**
     * Reads the next message from this stream.
     * 
     * <p>Advances the reader position after returning the message. If no messages
     * are currently available, behavior depends on the implementation:
     * <ul>
     *   <li>Block until a message arrives (blocking mode)</li>
     *   <li>Return null immediately (non-blocking mode)</li>
     *   <li>Wait for a configured timeout period</li>
     * </ul>
     * 
     * @return The next message from the stream, or null if end of stream or no messages available
     * @throws Exception If the read operation fails
     */
    T read() throws Exception;
}
