package kafkasql.io;

import kafkasql.runtime.stream.StreamReader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;

/**
 * Kafka-backed {@link StreamReader} with pluggable deserialization.
 * <p>
 * Polls a Kafka topic for {@code byte[]} values and deserializes them via
 * a caller-supplied {@link Deserializer}. Does not own the consumer — the
 * caller manages its lifecycle.
 *
 * @param <T> The type of messages read from the stream
 */
public final class ReadStream<T> implements StreamReader<T> {

    @FunctionalInterface
    public interface Deserializer<T> {
        T deserialize(byte[] data) throws Exception;
    }

    private final String streamName;
    private final KafkaConsumer<byte[], byte[]> consumer;
    private final Deserializer<T> deserializer;
    private final Duration pollTimeout;
    private Iterator<ConsumerRecord<byte[], byte[]>> currentBatch;

    public ReadStream(
        String streamName,
        KafkaConsumer<byte[], byte[]> consumer,
        Deserializer<T> deserializer,
        Duration pollTimeout
    ) {
        this.streamName = streamName;
        this.consumer = consumer;
        this.deserializer = deserializer;
        this.pollTimeout = pollTimeout != null ? pollTimeout : Duration.ofMillis(100);
        consumer.subscribe(Collections.singletonList(streamName));
    }

    public ReadStream(
        String streamName,
        KafkaConsumer<byte[], byte[]> consumer,
        Deserializer<T> deserializer
    ) {
        this(streamName, consumer, deserializer, null);
    }

    @Override
    public String streamName() {
        return streamName;
    }

    @Override
    public T read() throws Exception {
        if (currentBatch != null && currentBatch.hasNext()) {
            return deserializer.deserialize(currentBatch.next().value());
        }

        ConsumerRecords<byte[], byte[]> records = consumer.poll(pollTimeout);
        if (records.isEmpty()) {
            return null;
        }

        currentBatch = records.iterator();
        if (currentBatch.hasNext()) {
            return deserializer.deserialize(currentBatch.next().value());
        }
        return null;
    }
}