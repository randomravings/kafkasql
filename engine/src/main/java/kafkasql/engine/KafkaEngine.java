package kafkasql.engine;

import java.nio.file.Path;
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
public abstract class KafkaEngine {
    
    /**
     * Execute a KafkaSQL script.
     * Parses, binds, validates, and executes all statements.
     * 
     * @param script The KafkaSQL source code
     * @throws RuntimeException if parsing/binding fails or execution error
     */
    public void execute(String script) {
        // Parse and bind
        Input input = new StringInput("script.kafka", script);
        KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
        ParseResult parseResult = KafkaSqlParser.parse(List.of(input), args);
        
        if (parseResult.diags().hasError()) {
            throw new RuntimeException("Parse error: " + parseResult.diags().errors());
        }
        
        SemanticModel model = KafkaSqlParser.bind(parseResult);
        if (model.hasErrors()) {
            throw new RuntimeException("Semantic error: " + model.allErrors());
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
        
        // Delegate to backend for querying
        // TODO: Pass type filters, WHERE clauses, projections to backend
        List<StreamRecord> records = readRecords(streamName);
        
        // For now, extract all StructValues
        // TODO: Apply filtering and projection here
        handleQueryResult(records);
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
