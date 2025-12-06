package kafkasql.cli;

import kafkasql.engine.KafkaSqlEngine;
import kafkasql.runtime.Name;
import kafkasql.runtime.value.StructValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory engine for interactive CLI sessions.
 * Stores all records in memory and maintains state across statements.
 */
public class InteractiveEngine extends KafkaSqlEngine {
    
    private final Map<Name, List<StreamRecord>> streams = new HashMap<>();
    private List<StructValue> lastQueryResult = new ArrayList<>();
    private List<String> lastShowResult = new ArrayList<>();
    private String lastExplainResult = "";
    
    @Override
    protected void writeRecord(Name streamName, String typeName, StructValue value) {
        streams.computeIfAbsent(streamName, k -> new ArrayList<>())
            .add(new StreamRecord(typeName, value));
    }
    
    @Override
    protected List<StreamRecord> readRecords(Name streamName) {
        return streams.getOrDefault(streamName, List.of());
    }
    
    @Override
    protected void handleQueryResult(List<StreamRecord> records) {
        lastQueryResult = records.stream()
            .map(StreamRecord::value)
            .toList();
    }
    
    @Override
    protected void handleShowResult(List<String> results) {
        lastShowResult = new ArrayList<>(results);
    }
    
    @Override
    protected void handleExplainResult(String explanation) {
        lastExplainResult = explanation;
    }
    
    /**
     * Get the results from the last READ query.
     */
    public List<StructValue> getLastQueryResult() {
        return lastQueryResult;
    }
    
    /**
     * Get the results from the last SHOW statement.
     */
    public List<String> getLastShowResult() {
        return lastShowResult;
    }
    
    /**
     * Get the result from the last EXPLAIN statement.
     */
    public String getLastExplainResult() {
        return lastExplainResult;
    }
    
    /**
     * Get all streams for inspection.
     */
    public Map<Name, List<StreamRecord>> getAllStreams() {
        return streams;
    }
    
    /**
     * Clear all data (useful for reset).
     */
    public void clear() {
        streams.clear();
        lastQueryResult.clear();
        lastShowResult.clear();
        lastExplainResult = "";
    }
    
    /**
     * Clear only the cached results, not the model/streams.
     */
    public void clearResults() {
        lastQueryResult.clear();
        lastShowResult.clear();
        lastExplainResult = "";
    }
}
