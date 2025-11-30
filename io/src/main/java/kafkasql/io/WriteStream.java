package kafkasql.io;

import java.util.List;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

/**
 * A lightweight wrapper for producing Kafka records with type-safe serialization.
 * <p>
 * This class wraps a Kafka producer's send operation and provides type-safe
 * serialization through a {@link RecordSerializer}. It only handles serialization
 * and invoking {@link Producer#send(ProducerRecord)} - all other producer management
 * (lifecycle, flushing, callbacks, configuration, error handling) remains the
 * caller's responsibility.
 * <p>
 * Example usage:
 * <pre>{@code
 * Producer<byte[], byte[]> producer = new KafkaProducer<>(props);
 * 
 * try {
 *     WriteStream<MyRecord> stream = new WriteStream<>(producer, serializer);
 *     WriteRecord<MyRecord> result = stream.write("my-topic", myRecord);
 *     RecordMetadata metadata = result.future().get();
 * } finally {
 *     producer.flush();
 *     producer.close();
 * }
 * }</pre>
 * 
 * @param <T> The type of record values to write
 */
public final class WriteStream<T extends RecordValue<T>> {
    private static final Integer NO_PARTITION = null;
    private static final Long NO_TIMESTAMP = null;
    private static final List<Header> NO_HEADERS = java.util.List.of();
    private final Producer<byte[], byte[]> _producer;
    private final RecordSerializer<T> _serializer;

    /**
     * Constructs a WriteStream with the given Kafka producer and serializer.
     * <p>
     * <strong>Note:</strong> This constructor does not take ownership of the producer.
     * The caller remains responsible for producer lifecycle management.
     * 
     * @param producer The Kafka producer to write to
     * @param serializer The serializer to convert record values to byte arrays
     */
    public WriteStream(Producer<byte[], byte[]> producer, RecordSerializer<T> serializer) {
        _producer = producer;
        _serializer = serializer;
    }
    
    /**
     * Writes a record to the specified topic using automatic partitioning.
     * <p>
     * This method serializes the value and wraps {@link Producer#send(ProducerRecord)},
     * returning immediately. The actual write happens asynchronously. Use the returned
     * Future to block for acknowledgment or handle errors.
     * 
     * @param topic The Kafka topic to write to
     * @param value The record value to write
     * @return A WriteRecord containing the value and Future for the write operation
     */
    public WriteRecord<T> write(String topic, T value) {
        return write(topic, NO_PARTITION, NO_TIMESTAMP, value, NO_HEADERS);
    }

    /**
     * Writes a record to the specified topic with full control over partition, timestamp, and headers.
     * <p>
     * This method serializes the value and wraps {@link Producer#send(ProducerRecord)},
     * returning immediately. The actual write happens asynchronously. Use the returned
     * Future to block for acknowledgment or handle errors.
     * 
     * @param topic The Kafka topic to write to
     * @param partition The partition to write to, or null for automatic partitioning
     * @param timestamp The timestamp for the record, or null for current time
     * @param value The record value to write
     * @param headers Additional headers to include with the record
     * @return A WriteRecord containing the value and Future for the write operation
     */
    public WriteRecord<T> write(String topic, Integer partition, Long timestamp, T value, Iterable<Header> headers) {
        var key = _serializer.serializeKey(value);
        var val = _serializer.serialize(value);
        var rec = new ProducerRecord<byte[], byte[]>(topic, partition, timestamp, key, val);
        for(var h : headers)
            rec.headers().add(h);
        var fut = _producer.send(rec);
        return new WriteRecord<T>(value, fut);
    }
}
