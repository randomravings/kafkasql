package kafkasql.io;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import kafkasql.core.RecordValue;

public interface RecordDeserializer<T extends RecordValue<T>> {
    /**
     * Deserializes the given byte array into a record value.
     * @param data The raw byte array of the record
     * @param value The record value to populate
     * @return A ReadResult indicating the outcome of the deserialization
     */
    ReadResult deserialize(ConsumerRecord<Void, byte[]> baseRecord, T value);
}
