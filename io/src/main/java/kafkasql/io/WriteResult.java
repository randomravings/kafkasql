package kafkasql.io;

import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.RecordMetadata;

public final record WriteResult<T>(T value, Future<RecordMetadata> future) { }
