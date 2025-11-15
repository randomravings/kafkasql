package kafkasql.io;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public final record ReadResult<T> (ReadResultCode code, T value, ConsumerRecord<byte[], byte[]> record) { }
