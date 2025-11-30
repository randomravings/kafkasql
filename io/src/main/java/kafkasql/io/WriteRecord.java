package kafkasql.io;

import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * A lightweight record wrapper for write operations.
 * <p>
 * This record encapsulates a record value along with a Future representing
 * the metadata of the write operation. The Future allows asynchronous
 * tracking of the write result.
 *
 * @param <T> The type of record values being written
 */
public final record WriteRecord<T extends RecordValue<T>>(
    T value,
    Future<RecordMetadata> recordMetadata
) { }