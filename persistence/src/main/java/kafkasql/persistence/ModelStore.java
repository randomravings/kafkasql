package kafkasql.persistence;

import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.decl.Decl;
import kafkasql.runtime.Name;
import kafkasql.runtime.stream.StreamReader;
import sys.schema.SymbolEventLog;

import java.util.HashMap;
import java.util.Map;

/**
 * ModelStore — persistent symbol table backed by a Kafka topic.
 *
 * <p>This is the central integration point between the semantic model
 * ({@link SymbolTable}) and the event log persistence layer
 * ({@link EventLogWriter}/{@link EventLogReader}).
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li><b>Create</b> — construct with optional writer for Kafka persistence</li>
 *   <li><b>Load</b> — replay events from stream reader to rebuild symbol table state</li>
 *   <li><b>Use</b> — pass {@link #symbols()} to the engine for semantic binding</li>
 *   <li><b>Persist</b> — call {@link #onCreated}/{@link #onDropped} after DDL mutations</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Create store with Kafka-backed writer
 * var store = new ModelStore(eventLogWriter);
 *
 * // Restore state from topic on startup
 * store.load(streamReader);
 *
 * // Wire into engine
 * engine.setSymbolTable(store.symbols());
 * engine.setModelChangeListener(store::onCreated);
 *
 * // Execute DDL — engine detects new symbols and calls onCreated
 * engine.execute("CREATE CONTEXT com;");
 * </pre>
 */
public class ModelStore {

    private final SymbolTable symbols;
    private final Map<Name, Integer> versions;
    private EventLogWriter writer;

    /**
     * Creates an in-memory model store (no Kafka persistence).
     */
    public ModelStore() {
        this.symbols = new SymbolTable();
        this.versions = new HashMap<>();
    }

    /**
     * Creates a model store with Kafka persistence.
     *
     * @param writer Pre-configured event log writer for persisting mutations
     */
    public ModelStore(EventLogWriter writer) {
        this();
        this.writer = writer;
    }

    /**
     * Returns the underlying symbol table for semantic binding.
     * Pass this to the engine via {@code engine.setSymbolTable(store.symbols())}.
     */
    public SymbolTable symbols() {
        return symbols;
    }

    /**
     * Sets or replaces the event log writer for persistence.
     */
    public void setWriter(EventLogWriter writer) {
        this.writer = writer;
    }

    /**
     * Loads state by replaying all events from a stream reader.
     * <p>
     * This rebuilds the symbol table from the backing Kafka topic.
     * Should be called once on startup before the engine begins
     * accepting new DDL statements.
     *
     * @param streamReader Reader for the SymbolEventLog topic
     * @return Number of events replayed
     * @throws Exception if reading or applying events fails
     */
    public int load(StreamReader<SymbolEventLog> streamReader) throws Exception {
        EventLogReader reader = new EventLogReader(streamReader, this.symbols);
        int count = reader.replayAll();
        // Sync version tracking for all loaded symbols
        for (Name name : symbols._decl.keySet()) {
            versions.putIfAbsent(name, 1);
        }
        return count;
    }

    /**
     * Persists a CREATE event for a newly registered symbol.
     * <p>
     * Called by the engine's model change listener after a successful
     * CREATE statement has been bound and validated.
     *
     * @param name           Fully qualified name of the created object
     * @param decl           The declaration AST node
     * @param statementText  The original DDL statement text
     * @throws Exception if writing the event fails
     */
    public void onCreated(Name name, Decl decl, String statementText) throws Exception {
        versions.put(name, 1);
        if (writer != null) {
            writer.writeCreate(name, decl, statementText);
            writer.flush();
        }
    }

    /**
     * Persists a DROP event for a removed symbol.
     *
     * @param name           Fully qualified name of the dropped object
     * @param statementText  The original DDL statement text
     * @throws Exception if writing the event fails
     */
    public void onDropped(Name name, String statementText) throws Exception {
        int version = versions.getOrDefault(name, 0) + 1;
        versions.put(name, version);
        symbols._decl.remove(name);
        if (writer != null) {
            writer.writeDrop(name, statementText, version);
            writer.flush();
        }
    }

    /**
     * Returns the current version of an object.
     * CREATE sets version to 1; each subsequent ALTER/DROP increments it.
     *
     * @param name Fully qualified name
     * @return Current version, or 0 if unknown
     */
    public int getVersion(Name name) {
        return versions.getOrDefault(name, 0);
    }
}
