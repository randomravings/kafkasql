package kafkasql.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
