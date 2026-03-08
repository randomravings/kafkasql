package kafkasql.runtime.stream;

/**
 * Interface for writing messages to a stream.
 * 
 * <p>Completely independent from {@link StreamReader} - implementations can be
 * write-only and don't need read capabilities. Provides sequential write access
 * to a stream.
 * 
 * <p>Implementations may support different write patterns:
 * <ul>
 *   <li>Publishing to message queues (Kafka, etc.)</li>
 *   <li>Appending to log files</li>
 *   <li>Inserting into databases</li>
 *   <li>Buffered in-memory accumulation</li>
 * </ul>
 * 
 * <p>Write operations may be:
 * <ul>
 *   <li><b>Synchronous</b> - Block until write is confirmed</li>
 *   <li><b>Asynchronous</b> - Return immediately, confirm later</li>
 *   <li><b>Batched</b> - Buffer multiple writes before flushing</li>
 * </ul>
 * 
 * @param <T> The type of messages written to this stream
 */
public interface StreamWriter<T> {
    
    /**
     * Returns the name of this stream.
     * 
     * @return The stream name
     */
    String streamName();
    
    /**
     * Writes a message to this stream.
     * 
     * <p>The message is appended to the stream. Depending on the implementation,
     * the write may be:
     * <ul>
     *   <li>Immediately persisted (synchronous)</li>
     *   <li>Buffered and flushed later (async/batched)</li>
     *   <li>Replicated to multiple locations</li>
     * </ul>
     * 
     * <p>Implementations should document their durability guarantees and
     * failure semantics.
     * 
     * @param message The message to write to the stream
     * @throws Exception If the write operation fails
     */
    void write(T message) throws Exception;
    
    /**
     * Flushes any buffered messages to ensure they are persisted.
     * 
     * <p>For implementations that buffer writes, this method ensures all
     * pending messages are written to durable storage. For implementations
     * without buffering, this may be a no-op.
     * 
     * @throws Exception If the flush operation fails
     */
    default void flush() throws Exception {
        // Default: no-op for unbuffered implementations
    }
}
