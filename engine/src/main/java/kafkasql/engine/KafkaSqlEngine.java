package kafkasql.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.runtime.Name;
import kafkasql.runtime.value.StructValue;

/**
 * KafkaEngine - Base execution engine for KafkaSQL scripts.
 * 
 * This engine handles:
 * 1. Parsing KafkaSQL source code
 * 2. Semantic binding and validation
 * 3. Extracting runtime values (StructValue) from bindings
 * 4. Delegating execution to backend-specific implementations
 * 
 * Subclasses implement the backend-specific operations:
 * - writeRecord(): Store records to a stream backend (Kafka, in-memory, etc.)
 * - readRecords(): Query records from a stream backend
 * 
 * Throws RuntimeException for parse/semantic errors or execution failures.
 */
public abstract class KafkaSqlEngine {
    
    private SemanticModel lastModel;
    
    /**
     * Execute a KafkaSQL script.
     * Parses, binds, validates, and executes all statements.
     * 
     * Note: CREATE statements are only visible within this single execute() call.
     * To share schema across multiple scripts, use executeAll().
     * 
     * @param script The KafkaSQL source code
     * @throws RuntimeException if parsing/binding fails or execution error
     */
    public void execute(String script) {
        executeAll(script);
    }
    
    /**
     * Execute multiple KafkaSQL scripts together in one binding session.
     * This allows CREATE statements in earlier scripts to be visible to later scripts.
     * 
     * @param scripts The KafkaSQL source codes to execute together
     * @throws RuntimeException if parsing/binding fails or execution error
     */
    public void executeAll(String... scripts) {
        List<Input> inputs = new ArrayList<>();
        for (int i = 0; i < scripts.length; i++) {
            inputs.add(new StringInput("script" + i + ".kafka", scripts[i]));
        }
        
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(inputs, args);
        
        if (parseResult.diags().hasError()) {
            String errorDetails = parseResult.diags().errors().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("Unknown parse error");
            throw new RuntimeException("Parse errors:\n" + errorDetails);
        }
        
        SemanticModel model = KafkaSqlParser.bind(parseResult);
        lastModel = model; // Store for inspection
        if (model.hasErrors()) {
            String errorDetails = model.diags().errors().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("Unknown semantic error");
            throw new RuntimeException("Semantic errors:\n" + errorDetails);
        }
        
        // Execute statements using bindings
        BindingEnv bindings = model.bindings();
        for (Script scriptNode : parseResult.scripts()) {
            for (Stmt stmt : scriptNode.statements()) {
                executeStatement(stmt, bindings);
            }
        }
    }
    
    /**
     * Execute a statement using runtime values from bindings.
     */
    private void executeStatement(Stmt stmt, BindingEnv bindings) {
        switch (stmt) {
            case WriteStmt write -> executeWrite(write, bindings);
            case ReadStmt read -> executeRead(read, bindings);
            default -> {
                // CREATE statements are handled during binding phase
            }
        }
    }
    
    /**
     * Execute WRITE: Extract StructValues from bindings and store via backend.
     */
    private void executeWrite(WriteStmt write, BindingEnv bindings) {
        Name streamName = Name.of(write.stream().context(), write.stream().name());
        String typeName = write.alias().name();
        
        // Each literal in VALUES(...) should be bound to a StructValue
        for (StructLiteralNode literal : write.values()) {
            Object bound = bindings.get(literal);
            if (bound instanceof StructValue structValue) {
                writeRecord(streamName, typeName, structValue);
            } else {
                throw new RuntimeException("Expected StructValue but got: " + 
                    (bound == null ? "null" : bound.getClass().getName()));
            }
        }
    }
    
    /**
     * Execute READ: Query stream via backend and apply filters.
     */
    private void executeRead(ReadStmt read, BindingEnv bindings) {
        Name streamName = Name.of(read.stream().context(), read.stream().name());
        
        // Get all records from the stream
        List<StreamRecord> allRecords = readRecords(streamName);
        
        // Filter by type if specific types are requested
        List<StreamRecord> filteredRecords;
        if (read.blocks().isEmpty()) {
            // No type blocks means read all
            filteredRecords = allRecords;
        } else {
            // Collect requested type names from type blocks
            java.util.Set<String> requestedTypes = read.blocks().stream()
                .map(block -> block.alias().name())
                .collect(java.util.stream.Collectors.toSet());
            
            // Filter records to only include requested types
            filteredRecords = allRecords.stream()
                .filter(record -> requestedTypes.contains(record.typeName()))
                .toList();
        }
        
        // TODO: Apply WHERE clauses and projections
        handleQueryResult(filteredRecords);
    }
    
    // ========================================================================
    // Abstract methods - implemented by backend-specific subclasses
    // ========================================================================
    
    /**
     * Write a record to the stream backend.
     * 
     * @param streamName Fully qualified stream name
     * @param typeName Type alias used in the WRITE statement
     * @param value The runtime struct value to write
     */
    protected abstract void writeRecord(Name streamName, String typeName, StructValue value);
    
    /**
     * Read records from the stream backend.
     * 
     * @param streamName Fully qualified stream name
     * @return List of records from the stream
     */
    protected abstract List<StreamRecord> readRecords(Name streamName);
    
    /**
     * Handle the result of a READ query.
     * Subclasses can override to capture/store query results.
     * 
     * @param records The records returned from the query
     */
    protected void handleQueryResult(List<StreamRecord> records) {
        // Default: no-op
        // Subclasses can override to store results for inspection
    }
    
    /**
     * Get the last semantic model from execution.
     * Useful for querying declared streams, types, contexts, etc.
     * 
     * @return The semantic model from the last executeAll() call, or null if not yet executed
     */
    public SemanticModel getLastModel() {
        return lastModel;
    }
    
    // ========================================================================
    // StreamRecord - Runtime record type
    // ========================================================================
    
    /**
     * StreamRecord - pairs a type name with a runtime StructValue.
     * This is the runtime representation (no AST dependencies).
     */
    public static record StreamRecord(
        String typeName,
        StructValue value
    ) {}
}
