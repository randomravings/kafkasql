package kafkasql.io;

import java.time.Duration;
import java.util.Iterator;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public abstract class KafkaSqlReader<T> {
    private final Consumer<byte[], byte[]> consumer;
    private final Duration pollTimeout;
    private Iterator<ConsumerRecord<byte[], byte[]>> iterator = null;

    protected KafkaSqlReader(Consumer<byte[], byte[]> consumer, Duration pollTimeout) {
        this.consumer = consumer;
        this.pollTimeout = pollTimeout;
    }

    public ReadResult<T> read() {
        if (iterator == null) {
            var records = consumer.poll(pollTimeout);
            if (records.isEmpty())
                return null;
            else
                iterator = records.iterator();
        }
        
        if (!iterator.hasNext()) {
            iterator = null;
            return read();
        }

        var record = iterator.next();
        T value = null;
        var resultCode = deserialize(record.value(), value);
        return new ReadResult<>(resultCode, value, record);
    }

    protected abstract ReadResultCode deserialize(byte[] data, T value);
}
