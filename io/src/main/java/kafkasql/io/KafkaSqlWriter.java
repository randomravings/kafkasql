package kafkasql.io;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public abstract class KafkaSqlWriter<T> {
    private final Producer<byte[], byte[]> producer;
    private final String topic;

    protected KafkaSqlWriter(Producer<byte[], byte[]> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    public WriteResult<T> write(T value) {
        var k = serializeKey(value);
        var v = serializeValue(value);
        var t = getTimestamp(value);
        var record = new ProducerRecord<byte[], byte[]>(topic, null, t, k, v);
        var future = producer.send(record);
        return new WriteResult<>(value, future);
    }

    protected byte[] serializeKey(T value) {
        return null;
    }

    protected long getTimestamp(T value) {
        return System.currentTimeMillis();
    }

    protected abstract byte[] serializeValue(T value);
}
