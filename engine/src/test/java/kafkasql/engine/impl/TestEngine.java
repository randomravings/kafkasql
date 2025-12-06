package kafkasql.engine.impl;

import java.util.*;

import kafkasql.engine.KafkaSqlEngine;
import kafkasql.runtime.Name;
import kafkasql.runtime.value.StructValue;

/**
 * TestEngine - In-memory implementation of KafkaEngine for testing.
 * 
 * This engine provides a lightweight way to test KafkaSQL scripts without requiring
 * actual Kafka infrastructure. All data is stored in memory.
 * 
 * Key design: The engine's internal operations work ONLY with runtime types
 * (StructValue, StructType, etc.) - no AST or semantic dependencies.
 */
public final class TestEngine extends KafkaSqlEngine {
    
    // In-memory storage: stream name -> list of records
    private final Map<Name, List<StreamRecord>> streams;
    
    // Last execution result for inspection
    private List<StructValue> lastQueryResult;
    
    public TestEngine() {
        this.streams = new HashMap<>();
        this.lastQueryResult = Collections.emptyList();
    }
    
    // ========================================================================
    // Backend implementation
    // ========================================================================
    
    @Override
    protected void writeRecord(Name streamName, String typeName, StructValue value) {
        List<StreamRecord> records = streams.computeIfAbsent(streamName, k -> new ArrayList<>());
        records.add(new StreamRecord(typeName, value));
    }
    
    @Override
    protected List<StreamRecord> readRecords(Name streamName) {
        return new ArrayList<>(streams.getOrDefault(streamName, Collections.emptyList()));
    }
    
    @Override
    protected void handleQueryResult(List<StreamRecord> records) {
        // Store results for inspection
        this.lastQueryResult = records.stream()
            .map(StreamRecord::value)
            .toList();
    }
    
    // ========================================================================
    // Testing/inspection methods
    // ========================================================================
    
    /**
     * Get results from the last READ query.
     */
    public List<StructValue> getLastQueryResult() {
        return Collections.unmodifiableList(lastQueryResult);
    }
    
    /**
     * Get all records in a stream (for testing/debugging).
     */
    public List<StreamRecord> getStream(Name streamName) {
        return Collections.unmodifiableList(
            streams.getOrDefault(streamName, Collections.emptyList())
        );
    }
    
    /**
     * Get all stream names.
     */
    public Set<Name> getStreamNames() {
        return Collections.unmodifiableSet(streams.keySet());
    }
    
    /**
     * Clear all data.
     */
    public void reset() {
        streams.clear();
        lastQueryResult = Collections.emptyList();
    }
}
