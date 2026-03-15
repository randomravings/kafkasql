package kafkasql.io;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;

/**
 * Wire-format definition for schema-change markers on data topics.
 * <p>
 * A schema marker is a Kafka record that signals readers to sync the
 * event log and re-resolve the schema before consuming further data.
 * It is written to the same topic as regular data records whenever an
 * ALTER TYPE changes a type referenced by a stream.
 *
 * <h3>Wire format</h3>
 * <ul>
 *   <li><b>Key</b>: the type name (UTF-8 string)</li>
 *   <li><b>Value</b>: empty ({@code new byte[0]})</li>
 *   <li><b>Header</b>: {@value #HEADER} → type name (UTF-8 bytes)</li>
 * </ul>
 */
public final class SchemaMarker {

    /** Kafka record header that distinguishes markers from data records. */
    public static final String HEADER = "__schema_marker__";

    private SchemaMarker() {}

    /**
     * Writes a schema-change marker to the given topic.
     *
     * @param producer  The Kafka producer to use
     * @param topic     The data topic to write the marker to
     * @param typeName  The type alias that was altered
     * @return metadata with partition and offset of the marker record
     */
    public static RecordMetadata write(
        KafkaProducer<String, byte[]> producer,
        String topic,
        String typeName
    ) throws Exception {
        ProducerRecord<String, byte[]> record =
            new ProducerRecord<>(topic, typeName, new byte[0]);
        record.headers().add(
            new RecordHeader(HEADER, typeName.getBytes(StandardCharsets.UTF_8)));
        return producer.send(record).get();
    }

    /**
     * Returns {@code true} if the record is a schema-change marker
     * (as opposed to a normal data record).
     *
     * @param record  The consumer record to inspect
     * @return true if the record carries the schema-marker header
     */
    public static boolean isMarker(ConsumerRecord<String, byte[]> record) {
        for (var header : record.headers()) {
            if (HEADER.equals(header.key())) {
                return true;
            }
        }
        return false;
    }
}
