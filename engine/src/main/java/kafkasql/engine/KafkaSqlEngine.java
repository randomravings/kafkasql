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
import kafkasql.lang.syntax.ast.expr.*;
import kafkasql.lang.syntax.ast.fragment.WhereNode;
import kafkasql.lang.syntax.ast.literal.*;
import kafkasql.lang.syntax.ast.show.ShowTarget;
import kafkasql.runtime.Name;
import kafkasql.runtime.diagnostics.Range;
import kafkasql.runtime.type.SchemaResolver;
import kafkasql.runtime.type.StructType;
import kafkasql.runtime.value.EnumValue;
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
        
        // Capture results for all statements in the last script only.
        // Earlier scripts (e.g. remote schema context) must not emit output.
        List<Script> orderedScripts = new ArrayList<>(parseResult.scripts());
        int lastScriptIdx = orderedScripts.size() - 1;
        
        for (int si = 0; si < orderedScripts.size(); si++) {
            boolean captureResults = (si == lastScriptIdx);
            for (Stmt stmt : orderedScripts.get(si).statements()) {
                executeStatement(stmt, bindings, captureResults);
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
            case UserStmt user -> executeUser(user);
            case AclStmt acl   -> executeGrant(acl);
            default -> {
                // CREATE and USE statements are handled during binding phase
            }
        }
    }

    protected void executeUser(UserStmt stmt) {
        // Default no-op; override in LSP to wire Kafka AdminClient
    }

    protected void executeGrant(AclStmt stmt) {
        // Default no-op; override in LSP to wire Kafka AdminClient ACL API
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
        
        // Apply WHERE clauses per type block
        if (!read.blocks().isEmpty()) {
            Map<String, Optional<WhereNode>> whereByType = new HashMap<>();
            for (ReadTypeBlock block : read.blocks()) {
                whereByType.put(block.alias().name(),
                    block.where().isPresent() ? Optional.of(block.where().get()) : Optional.empty());
            }
            filteredRecords = filteredRecords.stream()
                .filter(record -> {
                    Optional<WhereNode> wOpt = whereByType.get(record.typeName());
                    if (wOpt == null || wOpt.isEmpty()) return true;
                    return matchesWhere(wOpt.get().expr(), record);
                })
                .toList();
        }

        if (captureResults) {
            handleQueryResult(filteredRecords);
        }
    }

    // ── WHERE filter helpers ─────────────────────────────────────────────────

    private static boolean matchesWhere(Expr expr, StreamRecord record) {
        Map<String, Object> env = buildRecordEnv(record.typeName(), record.value());
        try {
            Object result = evalWhereExpr(expr, env);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return true; // keep record when expression cannot be evaluated
        }
    }

    /** Builds a flat env map for WHERE expression evaluation.
     *  - Each struct field is added under its own name.
     *  - Enum fields are normalised to their symbol name (String).
     *  - Nested StructValues are converted to sub-maps.
     *  - "Value" and the type name are added as aliases for the whole record
     *    so expressions like {@code Value.Status} and {@code CustomerRecord.Status}
     *    both resolve correctly. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildRecordEnv(String typeName, StructValue value) {
        Map<String, Object> flat = new java.util.LinkedHashMap<>();
        for (var e : value.fields().entrySet()) {
            flat.put(e.getKey(), normaliseWhereValue(e.getValue()));
        }
        // Allow TypeName.Field member access syntax alongside bare Field access
        flat.put(typeName, new java.util.LinkedHashMap<>(flat));
        return flat;
    }

    private static Object normaliseWhereValue(Object v) {
        if (v instanceof EnumValue ev) return ev.symbolName();
        if (v instanceof StructValue sv) {
            Map<String, Object> nested = new java.util.LinkedHashMap<>();
            for (var e : sv.fields().entrySet()) nested.put(e.getKey(), normaliseWhereValue(e.getValue()));
            return nested;
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Object evalWhereExpr(Expr expr, Map<String, Object> env) {
        return switch (expr) {
            case LiteralExpr lit    -> evalWhereLiteral(lit.literal());
            case IdentifierExpr id  -> env.get(id.name().name());
            case InfixExpr inf      -> evalWhereInfix(inf, env);
            case PrefixExpr pre     -> evalWherePrefix(pre, env);
            case PostfixExpr post   -> evalWherePostfix(post, env);
            case TrifixExpr tri     -> evalWhereTrifix(tri, env);
            case ParenExpr paren    -> evalWhereExpr(paren.inner(), env);
            case MemberExpr mem     -> evalWhereMember(mem, env);
            case IndexExpr idx -> null; // not supported in WHERE
        };
    }

    @SuppressWarnings("unchecked")
    private static Object evalWhereMember(MemberExpr mem, Map<String, Object> env) {
        Object target = evalWhereExpr(mem.target(), env);
        if (target instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(mem.name().name());
        }
        return null;
    }

    private static Object evalWhereLiteral(LiteralNode lit) {        return switch (lit) {
            case BoolLiteralNode   b -> b.value();
            case StringLiteralNode s -> s.value();
            case NullLiteralNode   n -> null;
            case EnumLiteralNode   e -> e.symbol().name(); // compare by symbol name
            case ListLiteralNode   l -> l.elements().stream().map(KafkaSqlEngine::evalWhereLiteral).toList();
            case NumberLiteralNode n -> {
                String t = n.text().replace("_", "");
                if (t.contains(".") || t.toLowerCase().contains("e")) yield Double.parseDouble(t);
                long val = Long.parseLong(t);
                // Use explicit if/else to avoid ternary type-promotion (int→long)
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) yield (int) val;
                yield val;
            }
            default -> null;
        };
    }

    private static Object evalWhereInfix(InfixExpr inf, Map<String, Object> env) {
        // Short-circuit AND/OR
        if (inf.op() == InfixOp.AND) {
            Object l = evalWhereExpr(inf.left(), env);
            return Boolean.TRUE.equals(l) && Boolean.TRUE.equals(evalWhereExpr(inf.right(), env));
        }
        if (inf.op() == InfixOp.OR) {
            Object l = evalWhereExpr(inf.left(), env);
            return Boolean.TRUE.equals(l) || Boolean.TRUE.equals(evalWhereExpr(inf.right(), env));
        }
        Object left  = evalWhereExpr(inf.left(),  env);
        Object right = evalWhereExpr(inf.right(), env);
        return switch (inf.op()) {
            case EQ   -> (left instanceof Number nl && right instanceof Number nr)
                             ? Double.compare(nl.doubleValue(), nr.doubleValue()) == 0
                             : java.util.Objects.equals(left, right);
            case NEQ  -> (left instanceof Number nl && right instanceof Number nr)
                             ? Double.compare(nl.doubleValue(), nr.doubleValue()) != 0
                             : !java.util.Objects.equals(left, right);
            case LT   -> whereCompare(left, right) < 0;
            case LTE  -> whereCompare(left, right) <= 0;
            case GT   -> whereCompare(left, right) > 0;
            case GTE  -> whereCompare(left, right) >= 0;
            case IN   -> right instanceof List<?> list && list.contains(left);
            case CONCAT -> String.valueOf(left) + String.valueOf(right);
            default   -> null;
        };
    }

    private static Object evalWherePrefix(PrefixExpr pre, Map<String, Object> env) {
        Object val = evalWhereExpr(pre.expr(), env);
        return switch (pre.op()) {
            case NOT -> !Boolean.TRUE.equals(val);
            case NEG -> val instanceof Number n ? -n.doubleValue() : null;
        };
    }

    private static Object evalWherePostfix(PostfixExpr post, Map<String, Object> env) {
        Object val = evalWhereExpr(post.expr(), env);
        return switch (post.op()) {
            case IS_NULL     -> val == null;
            case IS_NOT_NULL -> val != null;
        };
    }

    private static Object evalWhereTrifix(TrifixExpr tri, Map<String, Object> env) {
        Object v   = evalWhereExpr(tri.left(),   env);
        Object low = evalWhereExpr(tri.middle(),  env);
        Object hi  = evalWhereExpr(tri.right(),   env);
        return whereCompare(v, low) >= 0 && whereCompare(v, hi) <= 0;
    }

    @SuppressWarnings("unchecked")
    private static int whereCompare(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb)
            return Double.compare(na.doubleValue(), nb.doubleValue());
        if (a instanceof Comparable ca && b instanceof Comparable cb)
            return ca.compareTo(cb);
        return 0;
    }
    
    /**
     * Execute SHOW: Display metadata about contexts, types, or streams.
     */
    private void executeShow(ShowStmt show, boolean captureResults) {
        if (!captureResults) {
            return; // Don't capture results for replayed statements
        }

        switch (show) {
            case ShowCurrentStmt scs -> {
                String context = (currentContextName != null && !currentContextName.isEmpty())
                    ? currentContextName : "(global)";
                handleShowResult(List.of("Current context: " + context));
            }
            case ShowContextualStmt scs -> {
                // USERS does not require a schema — delegate to backend hook
                if (scs.target() == ShowTarget.USERS) {
                    handleShowResult(listUsers(scs.filter()));
                    return;
                }

                if (lastModel == null) {
                    handleShowResult(List.of("No schema loaded"));
                    return;
                }

                java.util.function.Predicate<Decl> predicate = switch (scs.target()) {
                    case CONTEXTS -> d -> d instanceof kafkasql.lang.syntax.ast.decl.ContextDecl;
                    case TYPES    -> d -> d instanceof kafkasql.lang.syntax.ast.decl.TypeDecl;
                    case STREAMS  -> d -> d instanceof kafkasql.lang.syntax.ast.decl.StreamDecl;
                    case USERS    -> throw new IllegalStateException("unreachable");
                };

                java.util.function.Predicate<String> namePredicate = buildNamePredicate(scs.filter());

                var results = lastModel.symbols()._decl.entrySet().stream()
                    .filter(e -> predicate.test(e.getValue()))
                    .map(e -> e.getKey().fullName())
                    .filter(namePredicate)
                    .sorted()
                    .toList();

                handleShowResult(results);
            }
        }
    }

    /**
     * Build a predicate that matches a fully-qualified name against an optional
     * glob pattern (supports {@code *} as a multi-character wildcard).
     * When no pattern is supplied every name matches.
     */
    private java.util.function.Predicate<String> buildNamePredicate(Optional<String> filter) {
        if (filter.isEmpty()) {
            return name -> true;
        }
        String pattern = filter.get();
        // Convert glob pattern to regex: escape regex metacharacters, then replace * with .*
        String regex = java.util.regex.Pattern.quote(pattern).replace("\\*", "\\E.*\\Q");
        java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
        return name -> compiled.matcher(name).matches();
    }

    /**
     * List users. Override in subclasses that have access to an AdminClient.
     * The base implementation always returns a placeholder message.
     *
     * @param filter when present, a glob pattern or exact username; when empty, list all
     */
    protected List<String> listUsers(Optional<String> filter) {
        return List.of("[SHOW USERS not supported in this mode]");
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
