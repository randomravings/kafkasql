package kafkasql.io;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * A lightweight wrapper for consuming Kafka records with type-safe deserialization.
 * <p>
 * This class wraps a Kafka consumer's poll operation and provides type-safe
 * deserialization through a {@link RecordDeserializer}. It handles deserialization
 * and provides a Future-based API for asynchronous reading. All other consumer
 * management (lifecycle, subscription, offset commits, configuration) remains the
 * caller's responsibility.
 * <p>
 * <strong>Important:</strong> Once closed, a ReadStream instance cannot be reused.
 * Any attempt to call {@link #read(Duration)} after closing will throw an
 * {@link IllegalStateException}.
 * <p>
 * Example usage with try-with-resources:
 * <pre>{@code
 * // Create and manage the consumer yourself
 * Consumer<Void, byte[]> consumer = new KafkaConsumer<>(props);
 * consumer.subscribe(Collections.singletonList("my-topic"));
 * try (ReadStream<MyRecord> stream = new ReadStream<>(consumer, deserializer)) {
 *     Future<ReadRecord<MyRecord>> future = stream.read(Duration.ofSeconds(10));
 *     try {
 *         ReadRecord<MyRecord> record = future.get();
 *         switch(record.status()) {
 *             case ACCEPTED:       // Successfully read record
 *                 // Process the record ...
 *                 break;
 *             case TYPE_EXCLUDED:  // TYPE not included in query
 *             case FILTERED_OUT:   // WHERE clause filtered out
 *             case SCHEMA:         // Schema change detected
 *                 // Optional: debug, log, offset management, etc. ...
 *                 break;
 *        }
 *     } catch (CancellationException e) {
 *         // Handle cancellation
 *     } catch (ExecutionException e) {
 *         // Handle read errors
 *     }
 * } finally {
 *     consumer.close();  // Your responsibility
 * }
 * }</pre>
 * <p>
 * The returned Future can be cancelled:
 * <pre>{@code
 * Future<ReadRecord<MyRecord>> future = stream.read(Duration.ofSeconds(10));
 * future.cancel(true); // Interrupts the read operation
 * }</pre>
 * 
 * @param <T> The type of record values to read
 */
public final class ReadStream<T extends RecordValue<T>> implements AutoCloseable {
    private Iterator<ConsumerRecord<Void, byte[]>> _current = Collections.emptyIterator();
    private final Consumer<Void, byte[]> _consumer;
    private final RecordDeserializer<T> _deserializer;
    private final ExecutorService _executor;
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    
    /**
     * Constructs a ReadStream with the given Kafka consumer and deserializer.
     * <p>
     * <strong>Note:</strong> This constructor does not take ownership of the consumer.
     * The caller remains responsible for consumer lifecycle management, including
     * subscription and closing.
     * 
     * @param consumer The Kafka consumer to read from
     * @param deserializer The deserializer to convert byte arrays to record values
     */
    public ReadStream(Consumer<Void, byte[]> consumer, RecordDeserializer<T> deserializer) {
        _consumer = consumer;
        _deserializer = deserializer;
        _executor = Executors.newSingleThreadExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!_closed.get()) {
                shutdownExecutor();
            }
        }));
    }

    /**
     * Reads a record from the stream asynchronously.
     * <p>
     * This method wraps {@link Consumer#poll(Duration)}, deserializes the record,
     * and returns a Future that completes when a record is available. The operation
     * runs on a background thread and <strong>blocks until a record is available or
     * the operation is cancelled</strong> via {@code future.cancel(true)}.
     * <p>
     * Note that the read will return a record for every polled Kafka record, regardless
     * of any filtering logic. It is the caller's responsibility to handle filtering by
     * inspecting the {@link ReadResult} in the returned {@link ReadRecord}.
     * <p>
     * The background thread will continuously poll the consumer until a record is
     * retrieved. Calling {@code future.get()} will block the calling thread until
     * the read completes or is cancelled.
     * 
     * @param pollDuration The maximum duration between consumer poll attempts
     * @return A Future that completes with the ReadRecord when available
     * @throws ReadCancelledException if the read operation is cancelled
     * @throws IllegalStateException if the ReadStream is closed
     */
    public Future<ReadRecord<T>> read(Duration pollDuration) {
        if (_closed.get())
            throw new IllegalStateException("ReadStream is closed");
        return CompletableFuture.supplyAsync(
            () -> readRecord(pollDuration),
            _executor
        );
    }
    
    /**
     * Closes this ReadStream and releases associated resources.
     * <p>
     * This method shuts down the internal executor service and ensures all
     * pending read operations are terminated. Once closed, this ReadStream
     * instance cannot be reused - any subsequent calls to {@link #read(Duration)}
     * will throw an {@link IllegalStateException}.
     * <p>
     * This method is idempotent and can be called multiple times safely.
     * <p>
     * <strong>Note:</strong> This method does not close the underlying Kafka consumer.
     * The consumer must be closed separately by the calling code.
     */
    @Override
    public void close() {
        if (_closed.compareAndSet(false, true)) {
            shutdownExecutor();
        }
    }

    private ReadRecord<T> readRecord(Duration pollDuration) {
        while(true) {
            if (Thread.currentThread().isInterrupted())
                throw new ReadCancelledException("Read operation interrupted");
            // Loop poll until we have data
            while (!_current.hasNext()) {
                var result = _consumer.poll(pollDuration);
                if (Thread.currentThread().isInterrupted())
                    throw new ReadCancelledException("Read operation interrupted");
                if (!result.isEmpty())
                    _current = result.iterator();
            }

            ConsumerRecord<Void, byte[]> baseRecord = _current.next();
            T value = null;
            ReadResult result = _deserializer.deserialize(baseRecord, value);
            return new ReadRecord<>(result, value, baseRecord);
        }
    }
    
    private void shutdownExecutor() {
        _executor.shutdown();
        try {
            if (!_executor.awaitTermination(5, TimeUnit.SECONDS)) {
                _executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            _executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}