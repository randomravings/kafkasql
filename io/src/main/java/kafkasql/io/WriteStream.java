package kafkasql.io;

import kafkasql.runtime.stream.StreamWriter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Kafka-backed {@link StreamWriter} with pluggable serialization.
 * <p>
 * Serializes messages via a caller-supplied {@link Serializer} and produces
 * them to a Kafka topic as {@code byte[]} key/value. Does not own the
 * producer — the caller manages its lifecycle.
 *
 * @param <T> The type of messages written to the stream
 */
public final class WriteStream<T> implements StreamWriter<T> {

    @FunctionalInterface
    public interface Serializer<T> {
        byte[] serialize(T data) throws Exception;
    }

    private final String streamName;
    private final KafkaProducer<byte[], byte[]> producer;
    private final Serializer<T> serializer;

    public WriteStream(
        String streamName,
        KafkaProducer<byte[], byte[]> producer,
        Serializer<T> serializer
    ) {
        this.streamName = streamName;
        this.producer = producer;
        this.serializer = serializer;
    }

    @Override
    public String streamName() {
        return streamName;
    }

    @Override
    public void write(T message) throws Exception {
        byte[] valueBytes = serializer.serialize(message);
        byte[] keyBytes = message.getClass().getSimpleName().getBytes();
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(streamName, keyBytes, valueBytes);
        producer.send(record).get();
    }

    @Override
    public void flush() throws Exception {
        producer.flush();
    }
}
