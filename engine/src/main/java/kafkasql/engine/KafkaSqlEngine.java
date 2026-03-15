package kafkasql.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.ParseResult;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.semantic.BindingEnv;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.lang.syntax.ast.show.ShowTarget;
import kafkasql.lang.syntax.ast.literal.StructLiteralNode;
import kafkasql.runtime.Name;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.runtime.type.SchemaResolver;
import kafkasql.runtime.type.StructType;
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
    protected String currentContextName = null; // Track current context for SHOW filtering
    private SymbolTable symbolTable;             // Persistent symbol table (null = ephemeral mode)
    private ModelChangeListener changeListener;  // Notified on DDL mutations
    private ModelDropListener dropListener;      // Notified on DDL drops
    private ModelAlterListener alterListener;    // Notified on DDL alters
    
    /**
     * Set a persistent symbol table for the engine.
     * <p>
     * When set, the engine reuses this symbol table across {@link #executeAll} calls
     * instead of creating a fresh one each time. New CREATE statements are detected
     * as delta changes and reported via the {@link ModelChangeListener}.
     * <p>
     * When null (default), the engine creates a fresh symbol table per call
     * (ephemeral mode — backward compatible).
     *
     * @param symbolTable Persistent symbol table, or null for ephemeral mode
     */
    public void setSymbolTable(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }
    
    /**
     * Returns the persistent symbol table, or null if in ephemeral mode.
     */
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
    
    /**
     * Set a listener for model mutations (CREATE/DROP).
     * <p>
     * The listener is only invoked in persistent mode (when a symbol table
     * has been set via {@link #setSymbolTable}). It receives the fully
     * qualified name, declaration, and original DDL text for each new symbol.
     *
     * @param listener Mutation listener, or null to disable
     */
    public void setModelChangeListener(ModelChangeListener listener) {
        this.changeListener = listener;
    }
    
    /**
     * Set a listener for DROP mutations.
     *
     * @param listener Drop listener, or null to disable
     */
    public void setModelDropListener(ModelDropListener listener) {
        this.dropListener = listener;
    }
    
    /**
     * Set a listener for ALTER mutations.
     *
     * @param listener Alter listener, or null to disable
     */
    public void setModelAlterListener(ModelAlterListener listener) {
        this.alterListener = listener;
    }
    
    /**
     * Set the current context name for contextual SHOW commands.
     */
    public void setCurrentContext(String contextName) {
        this.currentContextName = contextName;
    }
    
    /**
     * Get the current context name.
     */
    public String getCurrentContext() {
        return currentContextName;
    }
    
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
        // Build inputs and source map for statement text extraction
        List<Input> inputs = new ArrayList<>();
        Map<String, String> sourceMap = new HashMap<>();
        for (int i = 0; i < scripts.length; i++) {
            String sourceName = "script" + i + ".kafka";
            inputs.add(new StringInput(sourceName, scripts[i]));
            sourceMap.put(sourceName, scripts[i]);
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
        
        // Determine symbol table mode
        boolean persistent = (symbolTable != null);
        SymbolTable symbols = persistent ? symbolTable : new SymbolTable();
        Set<Name> beforeKeys = persistent ? new HashSet<>(symbols._decl.keySet()) : Set.of();
        
        SemanticModel model = KafkaSqlParser.bind(parseResult, symbols);
        lastModel = model; // Store for inspection
        
        if (model.hasErrors()) {
            // Rollback: remove any newly registered symbols in persistent mode
            if (persistent) {
                Set<Name> toRemove = new HashSet<>(symbols._decl.keySet());
                toRemove.removeAll(beforeKeys);
                for (Name key : toRemove) {
                    symbols._decl.remove(key);
                }
            }
            
            String errorDetails = model.diags().errors().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("Unknown semantic error");
            throw new RuntimeException("Semantic errors:\n" + errorDetails);
        }
        
        // Detect and notify new symbols in persistent mode
        if (persistent && changeListener != null) {
            Set<Name> newKeys = new HashSet<>(symbols._decl.keySet());
            newKeys.removeAll(beforeKeys);
            if (!newKeys.isEmpty()) {
                notifyNewSymbols(newKeys, symbols, parseResult, sourceMap);
            }
        }
        
        // Detect and notify dropped symbols in persistent mode
        if (persistent && dropListener != null) {
            Set<Name> droppedKeys = new HashSet<>(beforeKeys);
            droppedKeys.removeAll(symbols._decl.keySet());
            if (!droppedKeys.isEmpty()) {
                notifyDroppedSymbols(droppedKeys, parseResult, sourceMap);
            }
        }
        
        // Detect ALTER TYPE statements and write schema-change markers
        // to every stream that references the altered type.
        if (persistent) {
            notifyAlteredTypes(symbols, parseResult, sourceMap);
        }
        
        // Execute statements using bindings
        BindingEnv bindings = model.bindings();
        
        // Count total statements to know which is the last one
        int totalStatements = parseResult.scripts().stream()
            .mapToInt(script -> script.statements().size())
            .sum();
        int currentStatement = 0;
        
        for (Script scriptNode : parseResult.scripts()) {
            for (Stmt stmt : scriptNode.statements()) {
                currentStatement++;
                boolean isLastStatement = (currentStatement == totalStatements);
                executeStatement(stmt, bindings, isLastStatement);
            }
        }
    }
    
    /**
     * Execute a statement using runtime values from bindings.
     */
    private void executeStatement(Stmt stmt, BindingEnv bindings, boolean captureResults) {
        switch (stmt) {
            case WriteStmt write -> executeWrite(write, bindings);
            case ReadStmt read -> executeRead(read, bindings, captureResults);
            case ShowStmt show -> executeShow(show, captureResults);
            case ExplainStmt explain -> executeExplain(explain, captureResults);
            default -> {
                // CREATE and USE statements are handled during binding phase
            }
        }
    }
    
    /**
     * Execute WRITE: Extract StructValues from bindings and store via backend.
     */
    private void executeWrite(WriteStmt write, BindingEnv bindings) {
        Name streamName = Name.of(write.stream().context(), write.stream().name());
        String typeName = write.alias().name();
        
        // Get the current schema for this type from the write binding
        StructType schema = bindings.getOrNull(write, StructType.class);
        
        // Each literal in VALUES(...) should be bound to a StructValue
        for (StructLiteralNode literal : write.values()) {
            Object bound = bindings.get(literal);
            if (bound instanceof StructValue structValue) {
                // Resolve against current schema: fill defaults, strip dropped fields
                if (schema != null) {
                    var result = SchemaResolver.resolveWrite(structValue, schema);
                    if (result.hasError()) {
                        throw new RuntimeException("Write resolution failed: " + result.error());
                    }
                    structValue = result.resolved();
                }
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
    private void executeRead(ReadStmt read, BindingEnv bindings, boolean captureResults) {
        Name streamName = Name.of(read.stream().context(), read.stream().name());
        
        // Get all records from the stream
        List<StreamRecord> allRecords = readRecords(streamName);
        
        // Filter by type if specific types are requested
        List<StreamRecord> filteredRecords;
        if (read.blocks().isEmpty()) {
            // No type blocks means read all
            filteredRecords = allRecords;
        } else {
            // Build a map of type name → StructType from bindings for resolution
            Map<String, StructType> typeSchemas = new HashMap<>();
            for (ReadTypeBlock block : read.blocks()) {
                String typeName = block.alias().name();
                StructType rowType = bindings.getOrNull(block, StructType.class);
                if (rowType != null) {
                    typeSchemas.put(typeName, rowType);
                }
            }
            
            // Collect requested type names from type blocks
            java.util.Set<String> requestedTypes = typeSchemas.keySet();
            
            // Filter records to only include requested types, resolving schema
            filteredRecords = allRecords.stream()
                .filter(record -> requestedTypes.contains(record.typeName()))
                .map(record -> {
                    StructType schema = typeSchemas.get(record.typeName());
                    if (schema != null) {
                        StructValue resolved = SchemaResolver.resolveRead(
                            record.value().fields(), schema);
                        return new StreamRecord(record.typeName(), resolved);
                    }
                    return record;
                })
                .toList();
        }
        
        // TODO: Apply WHERE clauses and projections
        if (captureResults) {
            handleQueryResult(filteredRecords);
        }
    }
    
    /**
     * Execute SHOW: Display metadata about contexts, types, or streams.
     */
    private void executeShow(ShowStmt show, boolean captureResults) {
        if (!captureResults) {
            return; // Don't capture results for replayed statements
        }
        
        if (lastModel == null) {
            handleShowResult(List.of("No schema loaded"));
            return;
        }
        
        List<String> results = new ArrayList<>();
        var symbols = lastModel.symbols();
        
        switch (show) {
            case ShowCurrentStmt scs -> {
                // Return the current context with label
                String context = (currentContextName != null && !currentContextName.isEmpty()) 
                    ? currentContextName 
                    : "(global)";
                results.add("Current context: " + context);
            }
            
            case ShowAllStmt sas -> {
                // Show ALL: list all of the specified type globally
                results.addAll(getAllOfType(symbols, sas.target()));
            }
            
            case ShowContextualStmt scs -> {
                // Show contextual: filter by context if qname is present
                // If qname is empty, use current context if available
                Optional<Name> contextFilter = scs.qname()
                    .map(qn -> Name.of(qn.context(), qn.name()));
                
                // If no qname and we have a current context, use it
                if (contextFilter.isEmpty() && currentContextName != null && !currentContextName.isEmpty()) {
                    contextFilter = Optional.of(Name.of(currentContextName));
                }
                
                results.addAll(getFilteredByContext(symbols, scs.target(), contextFilter));
            }
        }
        
        handleShowResult(results);
    }
    
    /**
     * Get all declarations of a specific type globally.
     */
    private List<String> getAllOfType(SymbolTable symbols, ShowTarget target) {
        var predicate = switch (target) {
            case CONTEXTS -> (java.util.function.Predicate<Decl>) (d -> d instanceof kafkasql.lang.syntax.ast.decl.ContextDecl);
            case TYPES -> (java.util.function.Predicate<Decl>) (d -> d instanceof kafkasql.lang.syntax.ast.decl.TypeDecl);
            case STREAMS -> (java.util.function.Predicate<Decl>) (d -> d instanceof kafkasql.lang.syntax.ast.decl.StreamDecl);
        };
        
        var items = symbols._decl.entrySet().stream()
            .filter(e -> predicate.test(e.getValue()))
            .map(e -> e.getKey())
            .sorted((a, b) -> a.fullName().compareTo(b.fullName()))
            .map(Name::fullName)
            .toList();
        
        return items;
    }
    
    /**
     * Get declarations filtered by optional context.
     * Shows only direct children of the context (not nested grandchildren).
     */
    private List<String> getFilteredByContext(SymbolTable symbols, ShowTarget target, Optional<Name> contextFilter) {
        var predicate = switch (target) {
            case CONTEXTS -> (java.util.function.Predicate<Decl>) (d -> d instanceof kafkasql.lang.syntax.ast.decl.ContextDecl);
            case TYPES -> (java.util.function.Predicate<Decl>) (d -> d instanceof kafkasql.lang.syntax.ast.decl.TypeDecl);
            case STREAMS -> (java.util.function.Predicate<Decl>) (d -> d instanceof kafkasql.lang.syntax.ast.decl.StreamDecl);
        };
        
        var items = symbols._decl.entrySet().stream()
            .filter(e -> predicate.test(e.getValue()))
            .map(e -> e.getKey())
            .filter(name -> {
                String fullName = name.fullName();
                if (contextFilter.isEmpty()) {
                    // In global context: show only top-level items (no dots)
                    return !fullName.contains(".");
                } else {
                    // In specific context: show only direct children
                    String contextPrefix = contextFilter.get().fullName() + ".";
                    if (!fullName.startsWith(contextPrefix)) {
                        return false;
                    }
                    // Check if it's a direct child (no additional dots after the prefix)
                    String afterPrefix = fullName.substring(contextPrefix.length());
                    return !afterPrefix.contains(".");
                }
            })
            .sorted((a, b) -> a.fullName().compareTo(b.fullName()))
            .map(Name::fullName)
            .toList();
        
        return items;
    }
    
    /**
     * Execute EXPLAIN: Display the declaration for a symbol.
     */
    private void executeExplain(ExplainStmt explain, boolean captureResults) {
        if (!captureResults) {
            return; // Don't capture results for replayed statements
        }
        
        if (lastModel == null) {
            handleExplainResult("No schema loaded");
            return;
        }
        
        Name name = Name.of(explain.target().context(), explain.target().name());
        var symbols = lastModel.symbols();
        var decl = symbols._decl.get(name);
        
        if (decl == null) {
            handleExplainResult("Object not found: " + name.fullName());
            return;
        }
        
        // Format the declaration as a CREATE statement
        String explanation = formatDeclaration(name, decl);
        handleExplainResult(explanation);
    }
    
    /**
     * Format a declaration as a CREATE statement string.
     */
    private String formatDeclaration(Name name, kafkasql.lang.syntax.ast.decl.Decl decl) {
        switch (decl) {
            case kafkasql.lang.syntax.ast.decl.ContextDecl cd ->
                { return "CREATE CONTEXT " + name.fullName() + ";"; }
            
            case kafkasql.lang.syntax.ast.decl.TypeDecl td -> {
                String typeName = name.fullName();
                return switch (td.kind()) {
                    case kafkasql.lang.syntax.ast.decl.ScalarDecl sd ->
                        "CREATE TYPE " + typeName + " AS SCALAR ...;";
                    case kafkasql.lang.syntax.ast.decl.EnumDecl ed ->
                        "CREATE TYPE " + typeName + " AS ENUM (...);";
                    case kafkasql.lang.syntax.ast.decl.StructDecl sd ->
                        "CREATE TYPE " + typeName + " AS STRUCT (...);";
                    case kafkasql.lang.syntax.ast.decl.UnionDecl ud ->
                        "CREATE TYPE " + typeName + " AS UNION (...);";
                    default -> "CREATE TYPE " + typeName + " ...;";
                };
            }
            
            case kafkasql.lang.syntax.ast.decl.StreamDecl sd ->
                { return "CREATE STREAM " + name.fullName() + " (...);"; }
            
            default -> { return "Unknown declaration type"; }
        }
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
     * Write a schema-change marker to a stream topic.
     * Called after an ALTER TYPE modifies a type referenced by this stream.
     * Readers encountering this marker must sync the event log and
     * re-resolve the schema before reading further data.
     *
     * @param streamName Fully qualified stream name (topic)
     * @param typeName   The type alias that was altered
     * @return partition → offset map of the marker record
     */
    protected abstract Map<Integer, Long> writeSchemaMarker(Name streamName, String typeName);
    
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
     * Handle the result of a SHOW statement.
     * Subclasses can override to capture/display the results.
     * 
     * @param results List of strings to display (one per line)
     */
    protected void handleShowResult(List<String> results) {
        // Default: print to stdout
        for (String line : results) {
            System.out.println(line);
        }
    }
    
    /**
     * Handle the result of an EXPLAIN statement.
     * Subclasses can override to capture/display the result.
     * 
     * @param explanation The explanation string to display
     */
    protected void handleExplainResult(String explanation) {
        // Default: print to stdout
        System.out.println(explanation);
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
    // Model mutation detection
    // ========================================================================
    
    /**
     * Walks the parsed scripts to find CREATE statements for newly registered
     * symbols and notifies the change listener with the original DDL text.
     */
    private void notifyNewSymbols(
        Set<Name> newKeys,
        SymbolTable symbols,
        ParseResult parseResult,
        Map<String, String> sourceMap
    ) {
        for (Script script : parseResult.scripts()) {
            for (Stmt stmt : script.statements()) {
                if (stmt instanceof CreateStmt create) {
                    symbols.nameOf(create.decl()).ifPresent(name -> {
                        if (newKeys.contains(name)) {
                            String text = extractStatementText(sourceMap, create.range());
                            try {
                                changeListener.onSymbolCreated(name, create.decl(), text);
                            } catch (Exception e) {
                                throw new RuntimeException(
                                    "Failed to persist model change for: " + name, e
                                );
                            }
                        }
                    });
                }
            }
        }
    }
    
    /**
     * Walks the parsed scripts to find DROP statements for removed
     * symbols and notifies the drop listener with the original DDL text.
     */
    private void notifyDroppedSymbols(
        Set<Name> droppedKeys,
        ParseResult parseResult,
        Map<String, String> sourceMap
    ) {
        for (Script script : parseResult.scripts()) {
            for (Stmt stmt : script.statements()) {
                if (stmt instanceof DropStmt drop) {
                    Name target = Name.of(drop.target().context(), drop.target().name());
                    if (droppedKeys.contains(target)) {
                        String text = extractStatementText(sourceMap, drop.range());
                        try {
                            dropListener.onSymbolDropped(target, text);
                        } catch (Exception e) {
                            throw new RuntimeException(
                                "Failed to persist drop for: " + target, e
                            );
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Walks the parsed scripts to find ALTER TYPE statements. For each
     * altered type, locates every stream that references it and writes
     * a schema-change marker to the stream's topic. Collects the marker
     * offsets and persists the ALTER event with stream offset metadata.
     */
    private void notifyAlteredTypes(
        SymbolTable symbols,
        ParseResult parseResult,
        Map<String, String> sourceMap
    ) {
        for (Script script : parseResult.scripts()) {
            for (Stmt stmt : script.statements()) {
                if (stmt instanceof AlterStmt.AlterType alter) {
                    String typeName = alter.target().name();
                    Map<String, Map<Integer, Long>> streamOffsets = new HashMap<>();
                    // Find all streams that reference this type and write markers
                    for (var entry : symbols._decl.entrySet()) {
                        if (entry.getValue() instanceof kafkasql.lang.syntax.ast.decl.StreamDecl sd) {
                            for (var member : sd.streamTypes()) {
                                if (member.name().name().equals(typeName)) {
                                    Name streamName = entry.getKey();
                                    try {
                                        Map<Integer, Long> offsets = writeSchemaMarker(streamName, typeName);
                                        if (!offsets.isEmpty()) {
                                            streamOffsets.put(streamName.fullName(), offsets);
                                        }
                                    } catch (Exception e) {
                                        throw new RuntimeException(
                                            "Failed to write schema marker for: " + streamName, e
                                        );
                                    }
                                }
                            }
                        }
                    }
                    // Persist the ALTER to the event log with stream offsets
                    if (alterListener != null) {
                        Name target = Name.of(alter.target().context(), alter.target().name());
                        var decl = symbols._decl.get(target);
                        if (decl != null) {
                            String text = extractStatementText(sourceMap, alter.range());
                            try {
                                alterListener.onSymbolAltered(target, decl, text, streamOffsets);
                            } catch (Exception e) {
                                throw new RuntimeException(
                                    "Failed to persist ALTER for: " + target, e
                                );
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extracts the DDL statement text from the original source using Range coordinates.
     */
    private String extractStatementText(Map<String, String> sourceMap, Range range) {
        String content = sourceMap.get(range.source());
        if (content == null) return "";
        
        String[] lines = content.split("\n", -1);
        int startLine = range.from().ln() - 1; // 0-based
        int startCol = range.from().ch();
        int endLine = range.to().ln() - 1;
        int endCol = range.to().ch();
        
        if (startLine < 0 || startLine >= lines.length) return "";
        if (endLine < 0 || endLine >= lines.length) return "";
        
        if (startLine == endLine) {
            int end = Math.min(endCol, lines[startLine].length());
            return lines[startLine].substring(startCol, end);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(lines[startLine].substring(startCol));
        for (int i = startLine + 1; i < endLine; i++) {
            sb.append("\n").append(lines[i]);
        }
        sb.append("\n").append(lines[endLine], 0, Math.min(endCol, lines[endLine].length()));
        return sb.toString();
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
