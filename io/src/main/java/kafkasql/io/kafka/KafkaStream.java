package kafkasql.io.kafka;

import kafkasql.runtime.stream.CompiledStream;
import kafkasql.runtime.stream.StreamReader;
import kafkasql.runtime.stream.StreamWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;

/**
 * Kafka-based implementation combining {@link StreamReader} and {@link StreamWriter}.
 * 
 * <p>Provides read/write operations for {@link CompiledStream} types backed by Kafka topics.
 * Each stream instance is bound to a specific topic and handles serialization/deserialization
 * internally using {@link kafkasql.io.BinarySerializer}.
 * 
 * <p><b>Write operations:</b> Messages are serialized to byte arrays and sent to the 
 * configured Kafka topic. The message class name is used as the partition key.
 * 
 * <p><b>Read operations:</b> Polls the Kafka topic for new messages (byte arrays), 
 * deserializes them to typed messages. Maintains an internal iterator over the current 
 * batch of messages. When exhausted, polls for the next batch.
 * 
 * <p><b>Resource Management:</b> This class wraps but does not own the Kafka consumer/producer.
 * The caller is responsible for creating, configuring, and closing these resources.
 * 
 * @param <T> The compiled stream type (must extend CompiledStream)
 */
public class KafkaStream<T extends CompiledStream<T>> implements StreamReader<T>, StreamWriter<T> {
    
    private final String streamName;
    private final String topicName;
    private final Class<T> streamClass;
    private final KafkaProducer<byte[], byte[]> producer;
    private final KafkaConsumer<byte[], byte[]> consumer;
    private final Duration pollTimeout;
    
    private Iterator<ConsumerRecord<byte[], byte[]>> currentBatch;
    
    /**
     * Creates a new Kafka stream with separate producer and consumer.
     * 
     * @param streamName The logical name of this stream
     * @param topicName The Kafka topic name
     * @param streamClass The stream class for deserialization
     * @param producer Kafka producer for write operations (may be null for read-only streams)
     * @param consumer Kafka consumer for read operations (may be null for write-only streams)
     * @param pollTimeout How long to wait for messages when polling (default: 100ms)
     */
    public KafkaStream(
        String streamName,
        String topicName,
        Class<T> streamClass,
        KafkaProducer<byte[], byte[]> producer,
        KafkaConsumer<byte[], byte[]> consumer,
        Duration pollTimeout
    ) {
        this.streamName = streamName;
        this.topicName = topicName;
        this.streamClass = streamClass;
        this.producer = producer;
        this.consumer = consumer;
        this.pollTimeout = pollTimeout != null ? pollTimeout : Duration.ofMillis(100);
        
        // Subscribe consumer to topic if provided
        if (consumer != null) {
            consumer.subscribe(Collections.singletonList(topicName));
        }
    }
    
    /**
     * Creates a new Kafka stream with default poll timeout (100ms).
     */
    public KafkaStream(
        String streamName,
        String topicName,
        Class<T> streamClass,
        KafkaProducer<byte[], byte[]> producer,
        KafkaConsumer<byte[], byte[]> consumer
    ) {
        this(streamName, topicName, streamClass, producer, consumer, null);
    }
    
    @Override
    public String streamName() {
        return streamName;
    }
    
    @Override
    public void write(T message) throws Exception {
        if (producer == null) {
            throw new UnsupportedOperationException(
                "Write operation not supported - no producer configured for stream: " + streamName
            );
        }
        
        // Serialize the message
        byte[] keyBytes = message.getClass().getSimpleName().getBytes();
        java.io.ByteArrayOutputStream valueStream = new java.io.ByteArrayOutputStream();
        kafkasql.io.BinarySerializer.serialize(message, valueStream);
        byte[] valueBytes = valueStream.toByteArray();
        
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topicName, keyBytes, valueBytes);
        
        // Synchronous send for simplicity - could be made async with callbacks
        producer.send(record).get();
    }
    
    @Override
    public void flush() throws Exception {
        if (producer != null) {
            producer.flush();
        }
    }
    @Override
    public T read() throws Exception {
        if (consumer == null) {
            throw new UnsupportedOperationException(
                "Read operation not supported - no consumer configured for stream: " + streamName
            );
        }
        
        // If we have messages in current batch, return next one
        if (currentBatch != null && currentBatch.hasNext()) {
            byte[] valueBytes = currentBatch.next().value();
            return kafkasql.io.BinarySerializer.deserialize(streamClass, new java.io.ByteArrayInputStream(valueBytes));
        }
        
        // Poll for next batch of messages
        ConsumerRecords<byte[], byte[]> records = consumer.poll(pollTimeout);
        
        if (records.isEmpty()) {
            return null;  // No messages available
        }
        
        // Set up iterator for this batch
        currentBatch = records.iterator();
        
        // Return first message from batch
        if (currentBatch.hasNext()) {
            byte[] valueBytes = currentBatch.next().value();
            return kafkasql.io.BinarySerializer.deserialize(streamClass, new java.io.ByteArrayInputStream(valueBytes));
        }
        
        return null;
    }
    
    /**
     * Closes the underlying Kafka producer and consumer.
     * 
     * @throws Exception If close operation fails
     */
    public void close() throws Exception {
        if (producer != null) {
            producer.close();
        }
        if (consumer != null) {
            consumer.close();
        }
    }
    
    /**
     * Creates a Kafka-backed stream reader from a pre-configured consumer.
     * Called by generated stream interface static methods.
     * 
     * <p>The caller is responsible for creating and configuring the consumer,
     * including setting up byte[] deserializers, consumer group, and other properties.
     * The consumer is NOT owned by the returned StreamReader - the caller must
     * manage its lifecycle (closing, error handling, etc.).
     * 
     * <p>The topic name is derived from the stream class name.
     * 
     * @param streamClass The compiled stream class
     * @param consumer Pre-configured Kafka consumer with byte[] deserializers
     * @return A StreamReader instance wrapping the consumer
     */
    public static <T extends CompiledStream<T>> StreamReader<T> reader(
        Class<T> streamClass,
        KafkaConsumer<byte[], byte[]> consumer
    ) {
        String topicName = streamClassToTopicName(streamClass);
        return new KafkaStream<>(
            streamClass.getSimpleName(),
            topicName,
            streamClass,
            null,  // No producer for read-only
            consumer
        );
    }
    
    /**
     * Creates a Kafka-backed stream writer from a pre-configured producer.
     * Called by generated stream interface static methods.
     * 
     * <p>The caller is responsible for creating and configuring the producer,
     * including setting up byte[] serializers, acks policy, and other properties.
     * The producer is NOT owned by the returned StreamWriter - the caller must
     * manage its lifecycle (flushing, closing, error handling, etc.).
     * 
     * <p>The topic name is derived from the stream class name.
     * 
     * @param streamClass The compiled stream class
     * @param producer Pre-configured Kafka producer with byte[] serializers
     * @return A StreamWriter instance wrapping the producer
     */
    public static <T extends CompiledStream<T>> StreamWriter<T> writer(
        Class<T> streamClass,
        KafkaProducer<byte[], byte[]> producer
    ) {
        String topicName = streamClassToTopicName(streamClass);
        return new KafkaStream<>(
            streamClass.getSimpleName(),
            topicName,
            streamClass,
            producer,
            null  // No consumer for write-only
        );
    }
    
    /**
     * Converts a stream class name to a Kafka topic name.
     * Default implementation uses the simple class name as-is.
     * Override this method to customize topic naming conventions.
     */
    private static <T extends CompiledStream<T>> String streamClassToTopicName(Class<T> streamClass) {
        return streamClass.getSimpleName();
    }
}
