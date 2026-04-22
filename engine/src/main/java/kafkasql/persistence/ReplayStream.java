package kafkasql.persistence;

import kafkasql.runtime.stream.StreamReader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Full-replay {@link StreamReader} for event-sourced state reconstruction.
 *
 * <p>Uses manual partition assignment (not subscribe/group coordination):
 * <ol>
 *   <li>On the first {@link #read()} call: resolves all partitions, assigns them
 *       directly, seeks to the beginning, and records current end offsets.</li>
 *   <li>Polls until the consumer position reaches the end offsets recorded at
 *       construction time, then returns {@code null}.</li>
 * </ol>
 *
 * <p>Every instance performs a complete, stateless replay — appropriate when
 * the model is never persisted between sessions. Does not own the
 * {@link KafkaConsumer}; the caller manages its lifecycle.
 *
 * @param <T> The type of messages read from the stream
 */
public final class ReplayStream<T> implements StreamReader<T> {

    @FunctionalInterface
    public interface Deserializer<T> {
        T deserialize(byte[] data) throws Exception;
    }

    private final String topic;
    private final KafkaConsumer<byte[], byte[]> consumer;
    private final Deserializer<T> deserializer;
    private final Duration pollTimeout;

    private Iterator<ConsumerRecord<byte[], byte[]>> currentBatch;
    private Map<TopicPartition, Long> endOffsets; // null until first read()

    public ReplayStream(
        String topic,
        KafkaConsumer<byte[], byte[]> consumer,
        Deserializer<T> deserializer,
        Duration pollTimeout
    ) {
        this.topic        = topic;
        this.consumer     = consumer;
        this.deserializer = deserializer;
        this.pollTimeout  = pollTimeout != null ? pollTimeout : Duration.ofMillis(500);
    }

    public ReplayStream(
        String topic,
        KafkaConsumer<byte[], byte[]> consumer,
        Deserializer<T> deserializer
    ) {
        this(topic, consumer, deserializer, null);
    }

    @Override
    public String streamName() {
        return topic;
    }

    @Override
    public T read() throws Exception {
        if (endOffsets == null) {
            // Resolve partitions once; assign + seek + record end offsets before first poll.
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(PartitionInfo::partition)
                .map(p -> new TopicPartition(topic, p))
                .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            endOffsets = consumer.endOffsets(partitions);
        }

        if (currentBatch != null && currentBatch.hasNext()) {
            return deserializer.deserialize(currentBatch.next().value());
        }

        if (caughtUp()) {
            return null;
        }

        ConsumerRecords<byte[], byte[]> records = consumer.poll(pollTimeout);
        if (!records.isEmpty()) {
            currentBatch = records.iterator();
            return deserializer.deserialize(currentBatch.next().value());
        }

        // Empty poll but not yet at end — retry (partition may still be loading).
        return read();
    }

    private boolean caughtUp() {
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            long end = entry.getValue();
            if (end == 0) continue; // empty partition — nothing to read
            if (consumer.position(entry.getKey()) < end) return false;
        }
        return true;
    }
}
