package kafkasql.io;

import java.util.LinkedHashMap;

/**
 * A dynamic record value that can hold arbitrary fields.
 * <p>
 * This class extends LinkedHashMap to allow dynamic storage of fields
 * associated with a record. It also implements the RecordValue interface
 * to be compatible with the KafkaSQL type system.
 */
public class DynamicValue
    extends LinkedHashMap<String, Object>
    implements RecordValue<DynamicValue> {

    private final String _streamName;
    private final Integer _typeId;

    /**
     * Constructs a new DynamicValue with the specified stream name and type ID.
     * @param streamName Name of the stream
     * @param typeId Type identifier
     */
    public DynamicValue(String streamName, Integer typeId) {
        _streamName = streamName;
        _typeId = typeId;
    }

    /**
     * Gets the stream name of this dynamic value.
     * @return The stream name
     */
    public String streamName() {
        return _streamName;
    }

    /**
     * Gets the type ID of this dynamic value.
     * @return The type ID
     */
    public Integer typeId() {
        return _typeId;
    }
}
