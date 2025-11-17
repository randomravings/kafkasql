package kafkasql.io;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import kafkasql.core.RecordValue;

/**
 * A lightweight record wrapper for read operations.
 * <p>
 * This record encapsulates a deserialized record value along with the
 * original Kafka ConsumerRecord. This allows access to both the high-level
 * record value and the underlying Kafka metadata.
 *
 * @param <T> The type of record values being read
 */
public final record ReadRecord<T extends RecordValue<T>>(
    ReadResult status,
    T value,
    ConsumerRecord<Void, byte[]> baseRecord
) { }